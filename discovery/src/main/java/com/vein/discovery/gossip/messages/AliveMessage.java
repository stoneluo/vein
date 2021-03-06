package com.vein.discovery.gossip.messages;

import com.vein.discovery.NodeType;
import com.vein.discovery.pushpull.PushNodeState;
import com.vein.common.Address;

/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午11:24
 */
public class AliveMessage implements GossipContent {

    private String nodeId;
    private Address address;
    private long incarnation;
    private NodeType nodeType;

    public AliveMessage() {
    }

    public AliveMessage(String nodeId, Address address, long incarnation, NodeType nodeType) {
        this.nodeId = nodeId;
        this.address = address;
        this.incarnation = incarnation;
        this.nodeType = nodeType;
    }

    public AliveMessage(PushNodeState state) {
        this.nodeId = state.getNodeId();
        this.address = state.getAddress();
        this.incarnation = state.getIncarnation();
        this.nodeType = state.getType();
    }


    @Override
    public short getType() {
        return ALIVE;
    }

    @Override
    public String getNodeId() {
        return nodeId;
    }
    @Override
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    @Override
    public long getIncarnation() {
        return incarnation;
    }

    @Override
    public void setIncarnation(long incarnation) {
        this.incarnation = incarnation;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public void setNodeType(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    @Override
    public String toString() {
        return "AliveMessage{" +
            "nodeId='" + nodeId + '\'' +
            ", address=" + address +
            ", incarnation=" + incarnation +
            ", nodeType=" + nodeType +
            '}';
    }
}
