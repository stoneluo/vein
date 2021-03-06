package com.vein.discovery;

import com.vein.common.AbstractService;
import com.vein.common.Address;
import com.vein.common.utils.AddressUtil;
import com.vein.common.utils.ThreadUtil;
import com.vein.discovery.gossip.GossipFinishNotifier;
import com.vein.discovery.gossip.GossipMessageService;
import com.vein.discovery.gossip.GossipMessageServiceImpl;
import com.vein.discovery.gossip.MessageGossiper;
import com.vein.discovery.gossip.messages.AliveMessage;
import com.vein.discovery.gossip.messages.DeadMessage;
import com.vein.discovery.handler.GossipRequestHandler;
import com.vein.discovery.handler.PingMessageHandler;
import com.vein.discovery.handler.PushPullRequestHandler;
import com.vein.discovery.handler.RedirectPingHandler;
import com.vein.discovery.probe.ProbeService;
import com.vein.discovery.probe.ProbeServiceImpl;
import com.vein.discovery.probe.ProbeTask;
import com.vein.discovery.pushpull.PushPullService;
import com.vein.discovery.pushpull.PushPullServiceImpl;
import com.vein.discovery.pushpull.PushPullTask;
import com.vein.transport.api.ConnectionManager;
import com.vein.transport.api.Transport;
import com.vein.transport.api.TransportClient;
import com.vein.transport.api.TransportFactory;
import com.vein.transport.api.TransportServer;
import com.vein.transport.api.exceptions.BindException;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.vein.discovery.NodeStatus.ALIVE;

/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午9:00
 */
public class DiscoveryServerImpl extends AbstractService implements DiscoveryServer {

    private final Node localNode;
    private final ServerContext serverContext;
    private final DiscoveryConfig config;
    private final Nodes nodes;

    private final MessageGossiper gossiper;
    private GossipMessageService gossipMessageService;
    private PushPullService pushPullService;
    private ProbeService probeService;


    private volatile ScheduledFuture probeFuture;
    private volatile ScheduledFuture pushPullFuture;

    public DiscoveryServerImpl(DiscoveryConfig config) {
        this.config = config;
        Address address = AddressUtil.getLocalAddress(config.getBindPort());

        NodeType type = NodeType.valueOf(config.getNodeType());
        String nodeId = config.getNodeId(address);
        localNode = new Node(nodeId, address, type, 0, new Date(), ALIVE);

        Transport transport = TransportFactory.create(config.getTransportType());
        TransportClient client = transport.client();
        TransportServer server = transport.server();

        this.serverContext = new ServerContext(localNode.getNodeId(), client, server);

        nodes = new Nodes(nodeId);
        gossiper = new MessageGossiper(nodes, config);
    }


    @Override
    public void join() {
        logger.info("start join discovery cluster");
        String members = config.getMembers();
        List<Address> addresses = AddressUtil.parseList(members);

        int retryTimes = 3;
        int success = 0;
        while (retryTimes > 0) {
            for (Address address : addresses) {
                try {
                    pushPullService.pushPull(address);
                    success++;
                } catch (Exception e) {
                    logger.error("push and pull message from node[{}] failed,cased by ", address, e);
                }
            }
            retryTimes--;
            if (retryTimes == 0) {
                break;
            }

            if (success == 0) {
                ThreadUtil.sleepUnInterrupted(4000);
            }
        }
    }

    @Override
    public void left() {
        long incarnation = localNode.nextIncarnation();
        logger.info("node:{} trying to left, incarnation:{}", localNode, incarnation);
        DeadMessage message = new DeadMessage(localNode.nodeId, incarnation, localNode.nodeId);

        GossipFinishNotifier notifier = () -> {
            try {
                close();
            } catch (Exception e) {
                logger.error("node:{} left caught exception:{}", localNode, e);
            }
        };
        gossipMessageService.deadNode(message, notifier);
    }

    @Override
    public Nodes getNodes() {
        return nodes;
    }

    @Override
    protected void doStart() throws Exception {
        int port = config.getBindPort();
        TransportServer transportServer;
        try {
            transportServer = serverContext.getServer();
            transportServer.listen(port, connection -> logger.info("receive connection[{}]", connection.getConnectionId()));
        } catch (BindException e) {
            logger.error("bind port failed", e);
            throw new RuntimeException("init node service fail", e);
        }

        gossipMessageService = new GossipMessageServiceImpl(nodes, serverContext, gossiper, config.getSuspectTimeout());
        pushPullService = new PushPullServiceImpl(nodes, serverContext, gossipMessageService);
        probeService = new ProbeServiceImpl(nodes, config.getIndirectNodeNum());

        ConnectionManager manager = transportServer.getConnectionManager();
        manager.registerMessageHandler(new PushPullRequestHandler(pushPullService));
        manager.registerMessageHandler(new PingMessageHandler(probeService));
        manager.registerMessageHandler(new RedirectPingHandler(probeService));
        manager.registerMessageHandler(new GossipRequestHandler(gossipMessageService));

        AliveMessage message = new AliveMessage(localNode.getNodeId(), localNode.getAddress(), localNode.nextIncarnation(), localNode.getType());
        gossipMessageService.aliveNode(message, () -> logger.info("bootstrap alive success!"), true);

        doSchedule();
    }

    private void doSchedule() {
        ScheduledExecutorService executorService = serverContext.getExecutorService();

        ProbeTask probeTask = new ProbeTask(nodes, probeService, config, gossipMessageService);
        int probeInterval = config.getProbeInterval();
        probeFuture = executorService.scheduleWithFixedDelay(probeTask, probeInterval, probeInterval, TimeUnit.MILLISECONDS);

        PushPullTask pushPullTask = new PushPullTask(nodes, pushPullService);
        int pushPullInterval = config.getPushPullInterval();
        pushPullFuture = executorService.scheduleWithFixedDelay(pushPullTask, pushPullInterval, pushPullInterval, TimeUnit.MILLISECONDS);
    }

    @Override
    protected void doClose() throws Exception {
        cancelFutures();

        serverContext.getClient().close();
        serverContext.getServer().close();
    }


    private void cancelFutures() {
        if (probeFuture != null) {
            probeFuture.cancel(false);
        }

        if (pushPullFuture != null) {
            pushPullFuture.cancel(false);
        }
    }
}
