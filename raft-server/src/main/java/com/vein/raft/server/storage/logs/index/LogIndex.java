package com.vein.raft.server.storage.logs.index;

/**
 * @author shifeng.luo
 * @version created on 2017/11/5 下午2:14
 */
public class LogIndex {
    public static final int LENGTH = 8 + 4;

    private long index;
    private int offset;

    public LogIndex() {
    }

    public LogIndex(long index, int offset) {
        this.index = index;
        this.offset = offset;
    }

    public long getIndex() {
        return index;
    }

    public void setIndex(long index) {
        this.index = index;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
