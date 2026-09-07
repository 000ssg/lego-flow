package ssg.legoflow.messaging.stomp.core.transport;

import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.transport.StompTransport;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class InMemoryStompTransport implements StompTransport {

    private final BlockingQueue<ByteBuffer> sendQueue;
    private final BlockingQueue<ByteBuffer> receiveQueue;
    private final AtomicBoolean open = new AtomicBoolean(true);

    private InMemoryStompTransport(BlockingQueue<ByteBuffer> receiveQueue,
                                    BlockingQueue<ByteBuffer> sendQueue) {
        this.receiveQueue = receiveQueue;
        this.sendQueue = sendQueue;
    }

    public static InMemoryStompTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        return new InMemoryStompTransport[]{
                new InMemoryStompTransport(q1, q2),
                new InMemoryStompTransport(q2, q1)
        };
    }

    @Override
    public void send(ByteBuffer data) {
        if (!open.get()) throw new IllegalStateException("Transport is closed");
        var copy = ByteBuffer.allocate(data.remaining());
        copy.put(data);
        copy.flip();
        sendQueue.offer(copy);
    }

    @Override
    public int receiveWithTimeout(ByteBuffer buffer, long timeout, TimeUnit unit) {
        if (!open.get()) return -1;
        try {
            ByteBuffer data = receiveQueue.poll(timeout, unit);
            if (data == null || !open.get()) return -1;
            if (!data.hasRemaining()) return -1;
            int count = Math.min(buffer.remaining(), data.remaining());
            buffer.put(data);
            return count;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    @Override
    public int receive(ByteBuffer buffer) {
        return receiveWithTimeout(buffer, Long.MAX_VALUE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        open.set(false);
        receiveQueue.offer(ByteBuffer.allocate(0));
    }

    @Override
    public boolean isOpen() {
        return open.get();
    }
}
