package ssg.legoflow.network.ldap.transport;

import ssg.legoflow.service.channel.InMemoryByteTransport;

import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * In-memory transport for LDAP protocol testing.
 *
 * @since 0.3.0
 */
public final class InMemoryLdapTransport extends InMemoryByteTransport implements LdapTransport {

    public InMemoryLdapTransport(BlockingQueue<ByteBuffer> inbound, BlockingQueue<ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     */
    public static InMemoryLdapTransport[] createPair() {
        var q1 = new LinkedBlockingQueue<ByteBuffer>();
        var q2 = new LinkedBlockingQueue<ByteBuffer>();
        var a = new InMemoryLdapTransport(q1, q2);
        var b = new InMemoryLdapTransport(q2, q1);
        a.peerRef = b;
        b.peerRef = a;
        return new InMemoryLdapTransport[]{a, b};
    }
}