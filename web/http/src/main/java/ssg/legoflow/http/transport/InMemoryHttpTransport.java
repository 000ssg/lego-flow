package ssg.legoflow.http.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the HTTP protocol without TCP sockets.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 *
 * @since 0.3.0
 */
public final class InMemoryHttpTransport extends InMemoryByteTransport implements HttpTransport {

    public InMemoryHttpTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryHttpTransport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryHttpTransport a = new InMemoryHttpTransport(q1, q2);
        InMemoryHttpTransport b = new InMemoryHttpTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryHttpTransport[]{a, b};
    }
}
