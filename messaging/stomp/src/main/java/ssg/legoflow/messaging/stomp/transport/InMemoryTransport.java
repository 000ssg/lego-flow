package ssg.legoflow.messaging.stomp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

/**
 * In-memory transport for testing the STOMP protocol without TCP sockets.
 */
public final class InMemoryTransport extends InMemoryByteTransport implements StompTransport {

    public InMemoryTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                             java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     */
    public static InMemoryTransport[] createPair() {
        var q1 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var q2 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        return new InMemoryTransport[]{
                new InMemoryTransport(q1, q2),
                new InMemoryTransport(q2, q1)
        };
    }
}
