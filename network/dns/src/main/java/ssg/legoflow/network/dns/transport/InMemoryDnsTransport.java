package ssg.legoflow.network.dns.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for DNS testing without network.
 *
 * @since 0.3.0
 */
public final class InMemoryDnsTransport extends InMemoryByteTransport implements DnsTransport {

    public InMemoryDnsTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    public static InMemoryDnsTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryDnsTransport(q1, q2);
        var b = new InMemoryDnsTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryDnsTransport[]{a, b};
    }
}