package ssg.legoflow.media.rtp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for RTP testing.
 *
 * @since 0.3.0
 */
public final class InMemoryRtpTransport extends InMemoryByteTransport implements RtpByteTransport {

    public InMemoryRtpTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    public static InMemoryRtpTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryRtpTransport(q1, q2);
        var b = new InMemoryRtpTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryRtpTransport[]{a, b};
    }
}