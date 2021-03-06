package com.vein.discovery.gossip;

import com.vein.common.Address;
import com.vein.common.base.LoggerSupport;
import com.vein.discovery.Node;
import com.vein.discovery.NodeListener;
import com.vein.discovery.NodeStatus;
import com.vein.discovery.Nodes;
import com.vein.discovery.ServerContext;
import com.vein.discovery.gossip.messages.AliveMessage;
import com.vein.discovery.gossip.messages.DeadMessage;
import com.vein.discovery.gossip.messages.GossipContent;
import com.vein.discovery.gossip.messages.MemberMessage;
import com.vein.discovery.gossip.messages.SuspectMessage;
import com.vein.transport.api.Connection;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午11:23
 */
public class GossipMessageServiceImpl extends LoggerSupport implements GossipMessageService {
    private final CopyOnWriteArrayList<NodeListener> listeners = new CopyOnWriteArrayList<>();
    private final ConcurrentMap<String, SuspectTask> suspectTaskMap = new ConcurrentHashMap<>();

    private final Nodes nodes;
    private final ServerContext serverContext;
    private final MessageGossiper messageGossiper;
    private final int suspectTimeout;

    private final ScheduledExecutorService executorService;

    private GossipMessageNotifier messageNotifier;

    public GossipMessageServiceImpl(Nodes nodes, ServerContext serverContext, MessageGossiper messageGossiper, int suspectTimeout) {
        this.nodes = nodes;
        this.serverContext = serverContext;
        this.messageGossiper = messageGossiper;
        this.suspectTimeout = suspectTimeout;
        this.executorService = serverContext.getExecutorService();
    }

    @Override
    public void aliveNode(AliveMessage message, GossipFinishNotifier notifier, boolean bootstrap) {
        String toAliveNode = message.getNodeId();
        logger.info("node:{} receive alive message to node:{} ,bootstrap:{}", nodes.getSelf(), toAliveNode, bootstrap);

        Node node = new Node(message, NodeStatus.DEAD, new Date());
        Node existNode = nodes.addIfAbsent(node);
        if (existNode == null) {
            existNode = node;
        }

        existNode.lock();
        try {
            boolean isLocalNode = nodes.isLocalNode(toAliveNode);
            if (!isLocalNode && existNode.getConnection() == null) {
                Address address = message.getAddress();
                Connection connection = createConnection(address);
                if (connection == null) {
                    logger.warn("can't create connection to node address:{},ignore alive message", address);
                    return;
                }
                existNode.setConnection(connection);
            }

            long existIncarnation = existNode.getIncarnation();
            long aliveIncarnation = message.getIncarnation();
            if (!isLocalNode && aliveIncarnation <= existIncarnation) {
                return;
            }

            if (isLocalNode && aliveIncarnation < existIncarnation) {
                return;
            }

            deleteSuspectTask(toAliveNode);

            if (!bootstrap && isLocalNode) {
                if (aliveIncarnation == existIncarnation) {
                    return;
                }

                //当节点join或者被杀死重启时,本地incarnation可能比其它节点持有镜像的incarnation低
                refute(aliveIncarnation);
                logger.warn("refuting an alive message");
                return;
            }

            NodeStatus oldStatus = node.getStatus();
            if (oldStatus != NodeStatus.ALIVE) {
                node.setIncarnation(aliveIncarnation);
                node.setStatus(NodeStatus.ALIVE);
                node.setStatusChangeTime(new Date());
            }

            nodes.aliveNode(existNode);
            gossip(message,notifier);

            for (NodeListener listener : listeners) {
                if (oldStatus == NodeStatus.DEAD) {
                    listener.onJoin(existNode);
                } else {
                    listener.onUpdate(existNode);
                }
            }
        } finally {
            existNode.unlock();
        }
    }

    private void deleteSuspectTask(String nodeId) {
        SuspectTask task = suspectTaskMap.remove(nodeId);
        if (task != null) {
            task.cancel();
        }
    }

    private void refute(long incarnation) {
        logger.info("refute state, remote incarnation:{}", incarnation);
        Node localNode = nodes.getLocalNode();

        long newIncarnation = incarnation + 1;
        localNode.setIncarnation(newIncarnation);

        AliveMessage message = new AliveMessage(localNode.getNodeId(), localNode.getAddress(), newIncarnation, localNode.getType());
        gossip(message,null);
    }

    private Connection createConnection(Address address) {
        Connection connection = serverContext.getConnection(address);
        if (connection != null) {
            return connection;
        }
        return serverContext.createConnection(address);
    }

    @Override
    public void suspectNode(SuspectMessage message, GossipFinishNotifier notifier) {
        String toSuspectNode = message.getNodeId();
        logger.info("node:{} receive suspect message to node:{}", nodes.getSelf(), toSuspectNode);

        Node node = nodes.get(toSuspectNode);
        if (node == null) {
            logger.warn("receive suspect message of node:{},but it isn't in nodes", toSuspectNode);
            return;
        }

        node.lock();
        try {
            long suspectIncarnation = message.getIncarnation();
            long existIncarnation = node.getIncarnation();

            if (suspectIncarnation < existIncarnation) {
                logger.warn("receive suspect message of node:{}, suspect incarnation:{} less than current:{}", toSuspectNode, suspectIncarnation, existIncarnation);
                return;
            }

            NodeStatus oldStatus = node.getStatus();
            if (oldStatus != NodeStatus.ALIVE) {
                logger.warn("receive suspect message of node:{}, current status is {}", toSuspectNode, oldStatus);
                return;
            }

            if (nodes.isLocalNode(toSuspectNode)) {
                logger.warn("refute a suspect message from {}", message.getFrom());
                refute(message.getIncarnation());
                return;
            }

            gossip(message,notifier);

            node.setIncarnation(suspectIncarnation);
            node.setStatus(NodeStatus.SUSPECT);
            Date now = new Date();
            node.setStatusChangeTime(now);

            nodes.suspectNode(node);
            createSuspectTask(toSuspectNode, now);
        } finally {
            node.unlock();
        }
    }


    private void createSuspectTask(String nodeId, Date suspectTime) {
        SuspectTask task = new SuspectTask(nodeId, nodes, this, suspectTime);
        suspectTaskMap.put(nodeId, task);
        ScheduledFuture<?> future = executorService.schedule(task, suspectTimeout, TimeUnit.MILLISECONDS);
        task.setFuture(future);
    }

    @Override
    public void deadNode(DeadMessage message, GossipFinishNotifier notifier) {
        String toDeadNode = message.getNodeId();
        logger.info("node:{} receive dead message to node:{}", nodes.getSelf(), toDeadNode);

        Node node = nodes.get(toDeadNode);
        if (node == null) {
            logger.warn("receive dead message of node:{},but it isn't in nodes", toDeadNode);
            return;
        }

        node.lock();
        try {
            long deadIncarnation = message.getIncarnation();
            long existIncarnation = node.getIncarnation();

            if (deadIncarnation < existIncarnation) {
                logger.warn("receive dead message of node:{}, dead incarnation:{} less than current:{}", toDeadNode, deadIncarnation, existIncarnation);
                return;
            }

            NodeStatus oldStatus = node.getStatus();
            if (oldStatus == NodeStatus.DEAD) {
                logger.warn("receive dead message of node:{}, current status is dead", toDeadNode);
                return;
            }

            if (nodes.isLocalNode(toDeadNode)) {
                logger.warn("refute a dead message from {}", message.getFrom());
                refute(deadIncarnation);
                return;
            }

            logger.info("enqueue dead message:{}", message);
            gossip(message,notifier);
            node.setStatus(NodeStatus.DEAD);
            node.setIncarnation(deadIncarnation);
            node.setStatusChangeTime(new Date());

            nodes.deadNode(node);
            for (NodeListener listener : listeners) {
                listener.onLeave(node);
            }
        } finally {
            node.unlock();
        }
    }

    private void gossip(GossipContent content, GossipFinishNotifier notifier) {
        MemberMessage message = new MemberMessage(content);
        message.setNotifier(notifier);
        messageGossiper.gossip(message);
    }

    @Override
    public void addListener(NodeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void setMessageNotifier(GossipMessageNotifier messageNotifier) {
        this.messageNotifier = messageNotifier;
    }

    @Override
    public void handle(GossipRequest request) {
        List<GossipContent> contents = request.getContents();
        if (contents == null) {
            return;
        }
        for (GossipContent content : contents) {
            switch (content.getType()) {
                case GossipContent.ALIVE:
                    aliveNode((AliveMessage) content, null, false);
                    break;
                case GossipContent.SUSPECT:
                    suspectNode((SuspectMessage) content, null);
                    break;
                case GossipContent.DEAD:
                    deadNode((DeadMessage) content, null);
                    break;
                case GossipContent.USER:
                    messageNotifier.notify(content);
                    break;
                default:
                    logger.error("unknown com.sm.charge.memory.gossip message type:{}", content.getType());
                    throw new RuntimeException("unknown com.sm.charge.memory.gossip message type:" + content.getType());
            }
        }
    }
}
