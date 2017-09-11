package com.sm.finance.charge.cluster.discovery.gossip.messages;

import com.sm.finance.charge.cluster.discovery.pushpull.PushNodeState;
import com.sm.finance.charge.common.Address;

/**
 * @author shifeng.luo
 * @version created on 2017/9/11 下午11:24
 */
public class AliveMessage extends DeclareMessage{

    private String nodeId;
    private Address address;
    private long incarnation;

    public AliveMessage() {
    }

    public AliveMessage(String nodeId, Address address, long incarnation) {
        this.nodeId = nodeId;
        this.address = address;
        this.incarnation = incarnation;
    }

    public AliveMessage(PushNodeState state){
        this.nodeId = state.getNodeId();
        this.address = state.getAddress();
        this.incarnation = state.getIncarnation();
    }


    @Override
    public short getType() {
        return ALIVE;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public long getIncarnation() {
        return incarnation;
    }

    public void setIncarnation(long incarnation) {
        this.incarnation = incarnation;
    }

    @Override
    public String toString() {
        return "AliveMessage{" +
            "nodeId='" + nodeId + '\'' +
            ", address=" + address +
            ", incarnation=" + incarnation +
            '}';
    }
}