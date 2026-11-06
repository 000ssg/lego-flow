package ssg.legoflow.network.ftp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for FTP protocol testing.
 *
 * @since 0.3.0
 */
public final class InMemoryFtpTransport extends InMemoryByteTransport implements FtpTransport {

    public InMemoryFtpTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     */
    public static InMemoryFtpTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryFtpTransport(q1, q2);
        var b = new InMemoryFtpTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryFtpTransport[]{a, b};
    }
}