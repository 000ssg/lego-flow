package ssg.legoflow.media.rtsp.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for RTSP testing.
 *
 * @since 0.3.0
 */
public final class InMemoryRtspTransport extends InMemoryByteTransport implements RtspTransport {

    public InMemoryRtspTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    public static InMemoryRtspTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryRtspTransport(q1, q2);
        var b = new InMemoryRtspTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryRtspTransport[]{a, b};
    }
}