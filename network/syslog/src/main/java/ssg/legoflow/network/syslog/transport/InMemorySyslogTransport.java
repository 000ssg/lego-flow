package ssg.legoflow.network.syslog.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for Syslog testing.
 *
 * @since 0.3.0
 */
public final class InMemorySyslogTransport extends InMemoryByteTransport implements SyslogTransport {

    public InMemorySyslogTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    public static InMemorySyslogTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemorySyslogTransport(q1, q2);
        var b = new InMemorySyslogTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemorySyslogTransport[]{a, b};
    }
}