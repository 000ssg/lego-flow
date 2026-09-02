package ssg.legoflow.messaging.stomp.core.transport;

import ssg.legoflow.messaging.stomp.core.StompFrame;
import ssg.legoflow.messaging.stomp.core.transport.StompTransport;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
/**
 * In-memory STOMP transport for testing and demos.
 *
 * <p>Uses a pair of blocking queues to connect two endpoints. Messages sent
 * by one side are received by the other, enabling transport-agnostic testing
 * of the STOMP protocol without any network I/O.
 *
 * @since 0.1.0
 */
public class InMemoryStompTransport implements StompTransport {

    private final BlockingQueue<StompFrame> sendQueue;
    private final BlockingQueue<StompFrame> receiveQueue;
    private volatile boolean open = true;

    private InMemoryStompTransport(BlockingQueue<StompFrame> sendQueue,
                                    BlockingQueue<StompFrame> receiveQueue) {
        this.sendQueue = sendQueue;
        this.receiveQueue = receiveQueue;
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Messages sent on the first are received on the second, and vice versa.
     *
     * @return an array of two connected transports: [client-side, server-side]
     */
    public static InMemoryStompTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<StompFrame>();
        var q2 = new LinkedBlockingQueue<StompFrame>();
        return new InMemoryStompTransport[]{
                new InMemoryStompTransport(q1, q2),
                new InMemoryStompTransport(q2, q1)
        };
    }

    @Override
    public void send(StompFrame frame) {
        if (!open) throw new IllegalStateException("Transport is closed");
        sendQueue.offer(frame);
    }

    @Override
    public StompFrame receive() {
        try {
            return receiveQueue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for frame", e);
        }
    }

    /**
     * Non-blocking receive: returns {@code null} if no frame is available.
     *
     * @return the next frame, or null
     */
    public StompFrame tryReceive() {
        return receiveQueue.poll();
    }

    /**
     * Receive with a timeout.
     *
     * @param timeout the maximum time to wait
     * @param unit    the time unit
     * @return the next frame, or null if timeout elapsed
     */
    public StompFrame tryReceive(long timeout, TimeUnit unit) {
        try {
            return receiveQueue.poll(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void close() {
        open = false;
    }

    @Override
    public boolean isOpen() {
        return open;
    }
}
