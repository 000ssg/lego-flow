package ssg.legoflow.email.imap.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for testing the IMAP protocol without TCP sockets.
 *
 * @since 0.3.0
 */
public final class InMemoryImapTransport extends InMemoryByteTransport implements ImapTransport {

    public InMemoryImapTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return an array of two connected transports
     */
    public static InMemoryImapTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryImapTransport(q1, q2);
        var b = new InMemoryImapTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryImapTransport[]{a, b};
    }
}