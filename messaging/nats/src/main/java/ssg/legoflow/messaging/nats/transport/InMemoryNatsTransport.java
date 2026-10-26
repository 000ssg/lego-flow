package ssg.legoflow.messaging.nats.transport;

import ssg.legoflow.service.channel.InMemoryByteHeadBuffer;

/**
 * In-memory {@link NatsTransport} pair for testing the NATS protocol without TCP sockets.
 *
 * @since 0.1.0
 */
public final class InMemoryNatsTransport extends InMemoryByteHeadBuffer implements NatsTransport {

    public InMemoryNatsTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                                 java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return two connected transports
     */
    public static InMemoryNatsTransport[] createPair() {
        var q1 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var q2 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var a = new InMemoryNatsTransport(q1, q2);
        var b = new InMemoryNatsTransport(q2, q1);
        a.setPeer(b);
        b.setPeer(a);
        return new InMemoryNatsTransport[]{a, b};
    }
}
