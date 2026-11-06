package ssg.legoflow.coap.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the CoAP protocol without UDP.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 *
 * @since 0.3.0
 */
public final class InMemoryCoapTransport extends InMemoryByteTransport implements CoapTransport {

    public InMemoryCoapTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryCoapTransport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryCoapTransport a = new InMemoryCoapTransport(q1, q2);
        InMemoryCoapTransport b = new InMemoryCoapTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryCoapTransport[]{a, b};
    }
}
