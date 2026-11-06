package ssg.legoflow.email.smtp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the SMTP protocol without TCP sockets.
 *
 * @since 0.3.0
 */
public final class InMemorySmtpTransport extends InMemoryByteTransport implements SmtpTransport {

    public InMemorySmtpTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports
     */
    public static InMemorySmtpTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemorySmtpTransport(q1, q2);
        var b = new InMemorySmtpTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemorySmtpTransport[]{a, b};
    }
}