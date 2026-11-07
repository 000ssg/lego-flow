package ssg.legoflow.ssh.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for SSH protocol testing.
 *
 * @since 0.3.0
 */
public final class InMemorySshTransport extends InMemoryByteTransport implements ssg.legoflow.ssh.transport.spi.SshTransport {

    public InMemorySshTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     */
    public static InMemorySshTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemorySshTransport(q1, q2);
        var b = new InMemorySshTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemorySshTransport[]{a, b};
    }
}