package ssg.legoflow.database.mysql.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the MySQL protocol without TCP sockets.
 *
 * <p>Provides two connected transports where data sent on one arrives on the other.
 *
 * @since 0.3.0
 */
public final class InMemoryMysqlTransport extends InMemoryByteTransport implements MysqlTransport {

    public InMemoryMysqlTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports [client, server]
     */
    public static InMemoryMysqlTransport[] createPair() {
        BlockingQueue<ByteBuffer> q1 = new LinkedBlockingQueue<>();
        BlockingQueue<ByteBuffer> q2 = new LinkedBlockingQueue<>();
        InMemoryMysqlTransport a = new InMemoryMysqlTransport(q1, q2);
        InMemoryMysqlTransport b = new InMemoryMysqlTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryMysqlTransport[]{a, b};
    }
}
