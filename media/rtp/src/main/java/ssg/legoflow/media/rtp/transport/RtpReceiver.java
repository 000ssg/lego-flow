package ssg.legoflow.media.rtp.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.rtp.buffer.JitterBuffer;
import ssg.legoflow.media.rtp.codec.RtpCodec;
import ssg.legoflow.media.rtp.packet.RtpPacket;
import ssg.legoflow.media.rtp.session.RtpSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
/**
 * RTP packet receiver using UDP transport with virtual threads.
 *
 * <p>Listens for incoming RTP packets on the transport's RTP channel,
 * decodes them, feeds them into the jitter buffer, and notifies a
 * consumer when packets are ready for playout.
 *
 * <p>Runs on a virtual thread for efficient blocking I/O.
 *
 * @since 0.1.0
 */
public final class RtpReceiver implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RtpReceiver.class);

    private final RtpTransport transport;
    private final RtpSession session;
    private final JitterBuffer jitterBuffer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Thread receiveThread;

    /**
     * Creates a new RTP receiver.
     *
     * @param transport    the UDP transport
     * @param session      the RTP session
     * @param jitterBuffer the jitter buffer for packet reordering
     */
    public RtpReceiver(RtpTransport transport, RtpSession session, JitterBuffer jitterBuffer) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.session = Objects.requireNonNull(session, "session");
        this.jitterBuffer = Objects.requireNonNull(jitterBuffer, "jitterBuffer");
    }

    /**
     * Starts receiving RTP packets on a virtual thread.
     *
     * <p>Incoming packets are decoded, checked for SSRC collisions,
     * added to the session participant table, and inserted into the
     * jitter buffer. The provided consumer is notified for each
     * accepted packet.
     *
     * @param packetConsumer consumer called for each received packet
     */
    public void start(Consumer<RtpPacket> packetConsumer) {
        Objects.requireNonNull(packetConsumer, "packetConsumer");
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Receiver is already running");
        }

        receiveThread = Thread.ofVirtual()
                .name("rtp-receiver")
                .start(() -> receiveLoop(packetConsumer));
        LOG.info("RTP receiver started");
    }

    private void receiveLoop(Consumer<RtpPacket> packetConsumer) {
        ByteBuffer buffer = ByteBuffer.allocate(RtpTransport.MAX_PACKET_SIZE);
        while (running.get() && !transport.isClosed()) {
            try {
                buffer.clear();
                var sender = transport.receiveRtp(buffer);
                if (sender == null) continue;

                buffer.flip();
                RtpPacket packet = RtpCodec.decode(buffer);

                // Check for SSRC collision
                long ssrc = packet.header().ssrc();
                if (session.detectCollision(ssrc)) {
                    LOG.warn("Dropping packet due to SSRC collision: 0x{}",
                            Long.toHexString(ssrc));
                    continue;
                }

                // Update participant statistics
                var participant = session.getOrCreateParticipant(ssrc);
                participant.recordReceived(
                        packet.header().sequenceNumber(),
                        packet.payloadSize());

                // Insert into jitter buffer
                var result = jitterBuffer.insert(packet);
                if (result == JitterBuffer.InsertResult.ACCEPTED) {
                    packetConsumer.accept(packet);
                }

                LOG.trace("Received RTP packet: seq={}, ssrc=0x{}, result={}",
                        packet.header().sequenceNumber(),
                        Long.toHexString(ssrc), result);

            } catch (IOException e) {
                if (running.get()) {
                    LOG.error("Error receiving RTP packet", e);
                }
            } catch (Exception e) {
                LOG.warn("Failed to decode RTP packet", e);
            }
        }
        LOG.info("RTP receiver stopped");
    }

    /**
     * Returns whether the receiver is currently running.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Returns the jitter buffer used by this receiver.
     *
     * @return the jitter buffer
     */
    public JitterBuffer jitterBuffer() {
        return jitterBuffer;
    }

    @Override
    public void close() {
        if (running.compareAndSet(true, false)) {
            LOG.info("Stopping RTP receiver");
            Thread t = receiveThread;
            if (t != null) {
                t.interrupt();
            }
        }
    }
}
