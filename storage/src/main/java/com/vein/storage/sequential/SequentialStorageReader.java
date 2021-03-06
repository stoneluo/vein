package com.vein.storage.sequential;

import com.google.common.collect.Lists;

import com.vein.common.AbstractService;
import com.vein.common.NamedThreadFactory;
import com.vein.serializer.api.Serializer;
import com.vein.storage.api.StorageReader;
import com.vein.storage.api.exceptions.BadDataException;
import com.vein.storage.api.exceptions.StorageException;
import com.vein.storage.api.index.IndexFile;
import com.vein.storage.api.index.IndexFileManager;
import com.vein.storage.api.index.OffsetIndex;
import com.vein.storage.api.segment.Entry;
import com.vein.storage.api.segment.Segment;
import com.vein.storage.api.segment.SegmentManager;
import com.vein.storage.api.segment.SegmentReader;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author shifeng.luo
 * @version created on 2017/9/25 下午10:41
 */
public class SequentialStorageReader extends AbstractService implements StorageReader {
    private final ExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("AsyncWritePool"));
    private volatile Future<?> future;

    private final IndexFileManager indexFileManager;
    private final SegmentManager segmentManager;
    private final ReadBuffer buffer;
    private final Serializer serializer;
    private volatile SegmentReader reader;

    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>(1000 * 1000);

    SequentialStorageReader(IndexFileManager indexFileManager, SegmentManager segmentManager, Serializer serializer) {
        this.indexFileManager = indexFileManager;
        this.segmentManager = segmentManager;
        this.serializer = serializer;
        this.buffer = new ReadBuffer();
    }

    @Override
    public void readFrom(long sequence) throws IOException, BadDataException {
        IndexFile indexFile = indexFileManager.lookup(sequence);
        if (indexFile == null) {
            logger.error("can't find start sequence:{} index file", sequence);
            throw new IllegalArgumentException("can't find start sequence:" + sequence + " index file");
        }

        OffsetIndex offsetIndex = indexFile.lookup(sequence);
        if (offsetIndex == null) {
            logger.error("can't find offset index in index file for sequence:{}", sequence);
            throw new IllegalStateException("can't find offset index in index file for sequence:" + sequence);
        }

        Segment segment = segmentManager.get(indexFile.baseSequence());
        reader = segment.reader(buffer);
        reader.readFrom(offsetIndex.offset());

        Entry entry = reader.readEntry();
        while (entry != null && entry.head().sequence() < sequence) {
            entry = reader.readEntry();
        }

        if (entry == null) {
            logger.error("sequence:{} out of range", sequence);
            throw new IllegalArgumentException("sequence:" + sequence + " out of range");
        }

        queue.offer(new Message(entry));
    }

    @Override
    public <T> T read() throws BadDataException, StorageException {
        Message message = queue.poll();
        if (message == null) {
            return null;
        }

        if (message.error != null) {
            if (message.error instanceof BadDataException) {
                throw (BadDataException) message.error;
            }

            throw new StorageException(message.error);
        }

        return serializer.deserialize(message.data.payload());
    }

    @Override
    public <T> List<T> read(int expectCount) throws BadDataException, StorageException {
        List<T> result = Lists.newArrayListWithCapacity(expectCount);
        for (int i = 0; i < expectCount; i++) {
            T message = read();
            result.add(message);
        }
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        this.future = executorService.submit(this::readAsync);
    }

    @Override
    protected void doClose() {
        future.cancel(false);
    }


    private void readAsync() {
        while (started.get()) {
            Entry entry = null;
            Throwable error = null;
            try {
                entry = reader.readEntry();
            } catch (IOException | BadDataException e) {
                error = e;
            }

            if (error == null) {
                Segment segment = reader.getSegment();
                if (entry == null && !segment.isActive()) {//切换文件
                    try {
                        reader = segment.getNext().reader(buffer);
                    } catch (IOException e) {
                        error = e;
                    }
                }
            }

            Message message = new Message(entry, error);
            while (true) {
                try {
                    queue.put(message);
                    break;
                } catch (InterruptedException e) {
                    logger.warn("put message interrupt");
                }
            }
        }
    }


    private class Message {
        private Entry data;
        private Throwable error;

        Message(Entry data) {
            this.data = data;
        }

        Message(Entry data, Throwable error) {
            this.data = data;
            this.error = error;
        }
    }
}
