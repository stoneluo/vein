package com.vein.discovery.gossip;

import com.vein.common.base.LoggerSupport;
import com.vein.discovery.Node;
import com.vein.discovery.NodeStatus;
import com.vein.discovery.Nodes;
import com.vein.discovery.gossip.messages.DeadMessage;

import java.util.Date;
import java.util.concurrent.ScheduledFuture;

/**
 * @author shifeng.luo
 * @version created on 2017/9/19 下午1:22
 */
public class SuspectTask extends LoggerSupport implements Runnable {

    /**
     * 被猜疑的节点
     */
    private final String nodeId;

    private final Nodes nodes;

    private final GossipMessageService messageService;

    private final Date createTime;

    private volatile ScheduledFuture future;

    public SuspectTask(String nodeId, Nodes nodes, GossipMessageService messageService, Date createTime) {
        this.nodeId = nodeId;
        this.nodes = nodes;
        this.messageService = messageService;
        this.createTime = createTime;
    }


    @Override
    public void run() {
        logger.info("handle node[{}] suspect time out", nodeId);
        try {
            Node node = nodes.get(nodeId);
            if (node == null) {
                logger.warn("handle node:{} suspect timeout,but it's not in nodes", nodeId);
                return;
            }

            NodeStatus status = node.getStatus();
            if (status == NodeStatus.SUSPECT && node.getStatusChangeTime().equals(createTime)) {
                logger.info("marking node [{}] as failed by suspect timeout happened", nodeId);
                DeadMessage message = new DeadMessage(nodeId, node.getIncarnation(), nodes.getSelf());
                messageService.deadNode(message, () -> logger.info("node:{} dead message has gossiped", nodeId));
            }
        } catch (Throwable e) {
            logger.error("execute suspect task caught exception", e);
        }
    }

    public void cancel() {
        if (future != null) {
            future.cancel(false);
        }
    }

    public String getNodeId() {
        return nodeId;
    }


    public Date getCreateTime() {
        return createTime;
    }

    public ScheduledFuture getFuture() {
        return future;
    }

    public void setFuture(ScheduledFuture future) {
        this.future = future;
    }
}
