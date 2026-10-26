package ssg.legoflow.messaging.kafka.transport;

import ssg.legoflow.service.channel.InMemoryByteHeadBuffer;

/**
 * In-memory {@link KafkaTransport} pair for testing the Kafka protocol without TCP sockets.
 *
 * @since 0.1.0
 */
public final class InMemoryKafkaTransport extends InMemoryByteHeadBuffer implements KafkaTransport {

    public InMemoryKafkaTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                                  java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return two connected transports
     */
    public static InMemoryKafkaTransport[] createPair() {
        var q1 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var q2 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var a = new InMemoryKafkaTransport(q1, q2);
        var b = new InMemoryKafkaTransport(q2, q1);
        a.setPeer(b);
        b.setPeer(a);
        return new InMemoryKafkaTransport[]{a, b};
    }
}
