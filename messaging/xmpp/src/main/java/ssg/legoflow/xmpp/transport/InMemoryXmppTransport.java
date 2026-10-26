package ssg.legoflow.xmpp.transport;

import ssg.legoflow.service.channel.InMemoryByteHeadBuffer;

/**
 * In-memory {@link XmppTransport} pair for testing the XMPP protocol without TCP sockets.
 *
 * @since 0.1.0
 */
public final class InMemoryXmppTransport extends InMemoryByteHeadBuffer implements XmppTransport {

    public InMemoryXmppTransport(java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> inbound,
                                 java.util.concurrent.BlockingQueue<java.nio.ByteBuffer> outbound) {
        super(inbound, outbound);
    }

    /**
     * Creates a connected pair of in-memory transports.
     *
     * @return two connected transports
     */
    public static InMemoryXmppTransport[] createPair() {
        var q1 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var q2 = new java.util.concurrent.LinkedBlockingQueue<java.nio.ByteBuffer>();
        var a = new InMemoryXmppTransport(q1, q2);
        var b = new InMemoryXmppTransport(q2, q1);
        a.setPeer(b);
        b.setPeer(a);
        return new InMemoryXmppTransport[]{a, b};
    }
}
