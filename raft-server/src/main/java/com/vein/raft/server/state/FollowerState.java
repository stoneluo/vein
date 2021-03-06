package com.vein.raft.server.state;

import com.vein.raft.server.RaftMember;
import com.vein.raft.server.RaftState;
import com.vein.raft.server.ServerContext;
import com.vein.raft.server.events.AppendRequest;
import com.vein.raft.server.events.AppendResponse;
import com.vein.raft.server.state.support.timer.HeartbeatTimeoutTimer;

/**
 * @author shifeng.luo
 * @version created on 2017/10/10 下午10:50
 */
public class FollowerState extends AbstractState {

    private final HeartbeatTimeoutTimer timer;

    public FollowerState(ServerContext context, HeartbeatTimeoutTimer timer) {
        super(context);
        this.timer = timer;
    }

    @Override
    public RaftState state() {
        return RaftState.FOLLOWER;
    }

    @Override
    protected AppendResponse doHandle(AppendRequest request) {
        timer.reset();
        RaftMember member = context.getCluster().member(request.getSource());
        context.getCluster().setMaster(member);
        return super.doHandle(request);
    }

    @Override
    public void suspect() {
        timer.stop();
    }

    @Override
    public void wakeup() {
        logger.info("{} transfer to follower state", self.getNodeId());
        timer.start();
    }

}
