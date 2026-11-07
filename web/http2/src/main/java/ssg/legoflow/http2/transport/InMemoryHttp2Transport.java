package ssg.legoflow.http2.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the HTTP/2 protocol without TCP sockets.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 *
 * @since 0.3.0
 */
public final class InMemoryHttp2Transport extends InMemoryByteTransport implements Http2Transport {

    public InMemoryHttp2Transport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryHttp2Transport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryHttp2Transport a = new InMemoryHttp2Transport(q1, q2);
        InMemoryHttp2Transport b = new InMemoryHttp2Transport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryHttp2Transport[]{a, b};
    }
}
