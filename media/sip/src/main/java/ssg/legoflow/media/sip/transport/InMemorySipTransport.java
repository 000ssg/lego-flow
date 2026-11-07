package ssg.legoflow.media.sip.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory byte transport for SIP testing.
 *
 * <p>Provides a byte-level transport SPI for testing SIP protocol logic
 * without network. This implements {@link ssg.legoflow.service.channel.ByteTransport}
 * rather than the protocol-specific {@link SipTransport}.
 *
 * @since 0.3.0
 */
public final class InMemorySipTransport extends InMemoryByteTransport {

    public InMemorySipTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    public static InMemorySipTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemorySipTransport(q1, q2);
        var b = new InMemorySipTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemorySipTransport[]{a, b};
    }
}