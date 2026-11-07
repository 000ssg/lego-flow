package ssg.legoflow.database.redis.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the Redis protocol without TCP sockets.
 *
 * @since 0.3.0
 */
public final class InMemoryRedisTransport extends InMemoryByteTransport implements RedisTransport {

    public InMemoryRedisTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports
     */
    public static InMemoryRedisTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryRedisTransport(q1, q2);
        var b = new InMemoryRedisTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryRedisTransport[]{a, b};
    }
}