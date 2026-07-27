package ssg.legoflow.media.rtp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.rtp.codec.RtcpCodec;
import ssg.legoflow.media.rtp.codec.RtpCodec;
import ssg.legoflow.media.rtp.packet.RtpPacket;
import ssg.legoflow.media.rtp.rtcp.RtcpPacket;
import ssg.legoflow.media.rtp.session.RtpSession;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RTP packet sender using UDP transport.
 *
 * <p>Encodes and sends RTP and RTCP packets to a remote destination.
 * Tracks sending statistics in the associated {@link RtpSession}.
 *
 * @since 1.0.0
 */
public final class RtpSender {

    private static final Logger LOG = LoggerFactory.getLogger(RtpSender.class);

    private final RtpTransport transport;
    private final RtpSession session;
    private final SocketAddress destination;
    private final AtomicLong packetsSent = new AtomicLong();
    private final AtomicLong bytesSent = new AtomicLong();

    /**
     * Creates a new RTP sender.
     *
     * @param transport   the UDP transport
     * @param session     the RTP session
     * @param destination the remote destination address
     */
    public RtpSender(RtpTransport transport, RtpSession session, SocketAddress destination) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.session = Objects.requireNonNull(session, "session");
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    /**
     * Sends an RTP packet to the configured destination.
     *
     * @param packet the RTP packet to send
     * @throws IOException if sending fails
     */
    public void send(RtpPacket packet) throws IOException {
        ByteBuffer encoded = RtpCodec.encode(packet);
        transport.sendRtp(encoded, destination);

        packetsSent.incrementAndGet();
        bytesSent.addAndGet(packet.payloadSize());

        // Update session statistics
        session.localParticipant().recordSent(packet.payloadSize());

        LOG.trace("Sent RTP packet: seq={}, ts={}, pt={}",
                packet.header().sequenceNumber(),
                packet.header().timestamp(),
                packet.header().payloadType());
    }

    /**
     * Sends an RTCP packet to the configured destination (RTCP port).
     *
     * @param packet the RTCP packet to send
     * @throws IOException if sending fails
     */
    public void sendRtcp(RtcpPacket packet) throws IOException {
        ByteBuffer encoded = RtcpCodec.encode(packet);
        var rtcpDest = new java.net.InetSocketAddress(
                ((java.net.InetSocketAddress) destination).getAddress(),
                ((java.net.InetSocketAddress) destination).getPort() + 1
        );
        transport.sendRtcp(encoded, rtcpDest);
        LOG.trace("Sent RTCP packet: type={}", packet.packetType());
    }

    /** @return total RTP packets sent */
    public long packetsSent() { return packetsSent.get(); }

    /** @return total payload bytes sent */
    public long bytesSent() { return bytesSent.get(); }

    /** @return the destination address */
    public SocketAddress destination() { return destination; }
}
