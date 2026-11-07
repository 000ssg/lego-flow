package ssg.legoflow.http3.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the HTTP/3 protocol without QUIC/UDP.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 *
 * @since 0.3.0
 */
public final class InMemoryHttp3Transport extends InMemoryByteTransport implements Http3Transport {

    public InMemoryHttp3Transport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryHttp3Transport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryHttp3Transport a = new InMemoryHttp3Transport(q1, q2);
        InMemoryHttp3Transport b = new InMemoryHttp3Transport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryHttp3Transport[]{a, b};
    }
}
