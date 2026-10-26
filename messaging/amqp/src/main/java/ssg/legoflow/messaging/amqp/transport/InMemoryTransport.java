package ssg.legoflow.messaging.amqp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

/**
 * In-memory transport for testing the AMQP protocol without TCP sockets.
 *
 * @since 0.1.0
 */
public final class InMemoryTransport extends InMemoryByteTransport implements AmqpTransport {

    public InMemoryTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                             java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     * Data sent on {@code pair[0]} arrives on {@code pair[1]} and vice versa.
     *
     * @return an array of two connected transports
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
