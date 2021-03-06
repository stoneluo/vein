package com.vein.raft.server.events;

import com.vein.raft.server.RaftMessage;

/**
 * @author shifeng.luo
 * @version created on 2017/10/10 下午10:52
 */
public class VoteRequest extends RaftMessage {

    /**
     * 候选人的最后日志条目的索引值
     */
    private long lastLogIndex;

    /**
     * 候选人最后日志条目的任期号
     */
    private long lastLogTerm;

    public long getLastLogIndex() {
        return lastLogIndex;
    }

    public void setLastLogIndex(long lastLogIndex) {
        this.lastLogIndex = lastLogIndex;
    }

    public long getLastLogTerm() {
        return lastLogTerm;
    }

    public void setLastLogTerm(long lastLogTerm) {
        this.lastLogTerm = lastLogTerm;
    }

    @Override
    public String toString() {
        return "VoteRequest{" +
            "term=" + term +
            ", lastLogIndex=" + lastLogIndex +
            ", source='" + source + '\'' +
            ", lastLogTerm=" + lastLogTerm +
            ", destination='" + destination + '\'' +
            '}';
    }
}
