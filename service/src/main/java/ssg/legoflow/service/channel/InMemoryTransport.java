package ssg.legoflow.service.channel;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Generic in-memory transport for testing any {@link ByteTransport}-based protocol.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 * This eliminates the need for protocol-specific InMemory implementations.
 *
 * <p>Usage:
 * <pre>
 * {@code
 * InMemoryTransport<?> pair = InMemoryTransport.createPair();
 * ByteTransport client = pair[0];
 * ByteTransport server = pair[1];
 * }
 * </pre>
 *
 * @since 0.3.0
 */
public final class InMemoryTransport extends InMemoryByteTransport {

    /**
     * Creates a new in-memory transport.
     *
     * @param inbound queue for incoming data
     * @param outbound queue for outgoing data
     */
    public InMemoryTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryTransport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryTransport a = new InMemoryTransport(q1, q2);
        InMemoryTransport b = new InMemoryTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryTransport[]{a, b};
    }
}
