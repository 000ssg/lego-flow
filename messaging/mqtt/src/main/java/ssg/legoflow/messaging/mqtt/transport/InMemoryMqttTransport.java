package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.InMemoryByteTransport;

/**
 * In-memory MQTT transport for testing without TCP sockets.
 *
 * @since 0.2.0
 */
public final class InMemoryMqttTransport extends InMemoryByteTransport implements MqttTransport {

    public InMemoryMqttTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                                 java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports
     */
    public static InMemoryMqttTransport[] createPair() {
        var q1 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var q2 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        return new InMemoryMqttTransport[]{
                new InMemoryMqttTransport(q1, q2),
                new InMemoryMqttTransport(q2, q1)
        };
    }

    @Override
    public DataChannel getChannel() {
        return null;
    }
}
