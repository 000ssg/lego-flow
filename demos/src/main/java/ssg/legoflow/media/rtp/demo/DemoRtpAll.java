package ssg.legoflow.media.rtp.demo;

import ssg.legoflow.media.rtp.buffer.JitterBuffer;
import ssg.legoflow.media.rtp.codec.RtcpCodec;
import ssg.legoflow.media.rtp.codec.RtpCodec;
import ssg.legoflow.media.rtp.packet.RtpHeader;
import ssg.legoflow.media.rtp.packet.RtpPacket;
import ssg.legoflow.media.rtp.rtcp.*;
import ssg.legoflow.media.rtp.session.RtcpIntervalCalculator;
import ssg.legoflow.media.rtp.session.RtpParticipant;
import ssg.legoflow.media.rtp.session.RtpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

/**
 * Comprehensive demo of all RTP module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>RTP packet encode/decode — create, encode, decode RTP packets</li>
 *   <li>RTCP Sender Report — encode/decode SR with reception reports</li>
 *   <li>RTCP Receiver Report — encode/decode RR packets</li>
 *   <li>RTCP Source Description — encode/decode SDES with CNAME</li>
 *   <li>RTCP Goodbye — encode/decode BYE with reason</li>
 *   <li>Compound RTCP — encode/decode compound packets (SR + SDES)</li>
 *   <li>Jitter buffer — insert, poll, duplicate/late detection, adaptive delay</li>
 *   <li>RTP session — participant management, SSRC collision detection</li>
 *   <li>RTCP interval — bandwidth-based interval calculation</li>
 * </ol>
 *
 * @since 1.0.0
 */
public final class DemoRtpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoRtpAll.class);

    /** Set to {@code true} to use external RTP endpoints. */
    public static boolean USE_EXTERNAL = false;

    private DemoRtpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param rtpCodec         true if RTP encode/decode round-trip succeeded
     * @param senderReport     true if SR encode/decode round-trip succeeded
     * @param receiverReport   true if RR encode/decode round-trip succeeded
     * @param sourceDescription true if SDES encode/decode succeeded
     * @param goodbye          true if BYE encode/decode succeeded
     * @param compoundPacket   true if compound RTCP encode/decode succeeded
     * @param jitterBuffer     true if jitter buffer operations succeeded
     * @param rtpSession       true if session management succeeded
     * @param rtcpInterval     true if RTCP interval calculation is valid
     * @since 1.0.0
     */
    public record Results(
            boolean rtpCodec,
            boolean senderReport,
            boolean receiverReport,
            boolean sourceDescription,
            boolean goodbye,
            boolean compoundPacket,
            boolean jitterBuffer,
            boolean rtpSession,
            boolean rtcpInterval
    ) {}

    /**
     * Runs the comprehensive demo covering all RTP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 1.0.0
     */
    public static Results runAll() throws Exception {
        boolean rtpCodec = demoRtpCodec();
        boolean sr = demoSenderReport();
        boolean rr = demoReceiverReport();
        boolean sdes = demoSourceDescription();
        boolean bye = demoGoodbye();
        boolean compound = demoCompoundPacket();
        boolean jitter = demoJitterBuffer();
        boolean session = demoRtpSession();
        boolean interval = demoRtcpInterval();

        return new Results(
                rtpCodec, sr, rr, sdes, bye,
                compound, jitter, session, interval
        );
    }

    // ======================== 1. RTP PACKET ENCODE/DECODE ====================

    /**
     * Demonstrates RTP packet creation, encoding, and decoding.
     *
     * @since 1.0.0
     */
    static boolean demoRtpCodec() {
        LOG.info("=== 1. RTP Packet Encode/Decode ===");
        byte[] payload = "Hello RTP".getBytes();
        var packet = RtpPacket.of(96, 1, 160, 0x12345678L, payload);

        LOG.info("Original: pt={}, seq={}, ts={}, ssrc=0x{}, payload={} bytes",
                packet.header().payloadType(), packet.header().sequenceNumber(),
                packet.header().timestamp(), Long.toHexString(packet.header().ssrc()),
                packet.payloadSize());

        ByteBuffer encoded = RtpCodec.encode(packet);
        LOG.info("Encoded size: {} bytes", encoded.remaining());

        RtpPacket decoded = RtpCodec.decode(encoded);
        LOG.info("Decoded: pt={}, seq={}, ts={}, ssrc=0x{}",
                decoded.header().payloadType(), decoded.header().sequenceNumber(),
                decoded.header().timestamp(), Long.toHexString(decoded.header().ssrc()));

        boolean ptMatch = packet.header().payloadType() == decoded.header().payloadType();
        boolean seqMatch = packet.header().sequenceNumber() == decoded.header().sequenceNumber();
        boolean tsMatch = packet.header().timestamp() == decoded.header().timestamp();
        boolean ssrcMatch = packet.header().ssrc() == decoded.header().ssrc();
        boolean payloadMatch = java.util.Arrays.equals(packet.payload(), decoded.payload());

        return ptMatch && seqMatch && tsMatch && ssrcMatch && payloadMatch;
    }

    // ======================== 2. SENDER REPORT ===============================

    /**
     * Demonstrates RTCP Sender Report encode/decode.
     *
     * @since 1.0.0
     */
    static boolean demoSenderReport() {
        LOG.info("=== 2. RTCP Sender Report ===");
        var report = new ReceptionReport(0xAABBCCDDL, 25, 100, 50000L, 150L, 0L, 0L);
        var sr = new SenderReport(0x12345678L, 0x1234567890ABCDEFL, 320000L,
                1000L, 160000L, List.of(report));

        ByteBuffer encoded = RtcpCodec.encode(sr);
        LOG.info("SR encoded: {} bytes", encoded.remaining());

        RtcpPacket decoded = RtcpCodec.decode(encoded);
        boolean isSR = decoded instanceof SenderReport;
        if (isSR) {
            var decodedSR = (SenderReport) decoded;
            LOG.info("SR: ssrc=0x{}, packets={}, octets={}, reports={}",
                    Long.toHexString(decodedSR.ssrc()), decodedSR.senderPacketCount(),
                    decodedSR.senderOctetCount(), decodedSR.reportCount());
            boolean ssrcOk = decodedSR.ssrc() == 0x12345678L;
            boolean packetsOk = decodedSR.senderPacketCount() == 1000L;
            boolean reportsOk = decodedSR.reportCount() == 1;
            return ssrcOk && packetsOk && reportsOk;
        }
        return false;
    }

    // ======================== 3. RECEIVER REPORT =============================

    /**
     * Demonstrates RTCP Receiver Report encode/decode.
     *
     * @since 1.0.0
     */
    static boolean demoReceiverReport() {
        LOG.info("=== 3. RTCP Receiver Report ===");
        var report = new ReceptionReport(0x11111111L, 10, 5, 30000L, 80L, 0L, 0L);
        var rr = new ReceiverReport(0xAAAAAAAAL, List.of(report));

        ByteBuffer encoded = RtcpCodec.encode(rr);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        boolean isRR = decoded instanceof ReceiverReport;
        if (isRR) {
            var decodedRR = (ReceiverReport) decoded;
            LOG.info("RR: ssrc=0x{}, reports={}", Long.toHexString(decodedRR.ssrc()),
                    decodedRR.reportCount());
            return decodedRR.ssrc() == 0xAAAAAAAAL && decodedRR.reportCount() == 1;
        }
        return false;
    }

    // ======================== 4. SOURCE DESCRIPTION ==========================

    /**
     * Demonstrates RTCP Source Description encode/decode with CNAME.
     *
     * @since 1.0.0
     */
    static boolean demoSourceDescription() {
        LOG.info("=== 4. RTCP Source Description ===");
        var chunk = new SdesChunk(0x12345678L, List.of(
                new SdesItem(SdesItem.Type.CNAME, "user@example.com"),
                new SdesItem(SdesItem.Type.NAME, "Demo User"),
                new SdesItem(SdesItem.Type.TOOL, "Lego-Flow-RTP/1.0")
        ));
        var sdes = new SourceDescription(List.of(chunk));

        ByteBuffer encoded = RtcpCodec.encode(sdes);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        boolean isSDES = decoded instanceof SourceDescription;
        if (isSDES) {
            var decodedSDES = (SourceDescription) decoded;
            var decodedChunk = decodedSDES.chunks().getFirst();
            LOG.info("SDES: ssrc=0x{}, cname={}, items={}",
                    Long.toHexString(decodedChunk.ssrc()),
                    decodedChunk.cname().orElse("none"),
                    decodedChunk.items().size());

            boolean ssrcOk = decodedChunk.ssrc() == 0x12345678L;
            boolean cnameOk = decodedChunk.cname().isPresent()
                    && "user@example.com".equals(decodedChunk.cname().get());
            boolean itemsOk = decodedChunk.items().size() == 3;
            return ssrcOk && cnameOk && itemsOk;
        }
        return false;
    }

    // ======================== 5. GOODBYE =====================================

    /**
     * Demonstrates RTCP Goodbye encode/decode with reason.
     *
     * @since 1.0.0
     */
    static boolean demoGoodbye() {
        LOG.info("=== 5. RTCP Goodbye ===");
        var bye = new Goodbye(List.of(0x12345678L, 0xAABBCCDDL),
                Optional.of("Session ended normally"));

        ByteBuffer encoded = RtcpCodec.encode(bye);
        RtcpPacket decoded = RtcpCodec.decode(encoded);

        boolean isBYE = decoded instanceof Goodbye;
        if (isBYE) {
            var decodedBYE = (Goodbye) decoded;
            LOG.info("BYE: sources={}, reason={}",
                    decodedBYE.sourceCount(), decodedBYE.reason().orElse("none"));

            boolean sourcesOk = decodedBYE.sourceCount() == 2;
            boolean reasonOk = decodedBYE.reason().isPresent()
                    && "Session ended normally".equals(decodedBYE.reason().get());
            return sourcesOk && reasonOk;
        }
        return false;
    }

    // ======================== 6. COMPOUND RTCP ===============================

    /**
     * Demonstrates compound RTCP packet (SR + SDES) encode/decode.
     *
     * @since 1.0.0
     */
    static boolean demoCompoundPacket() {
        LOG.info("=== 6. Compound RTCP Packet ===");
        var sr = new SenderReport(0x12345678L, System.currentTimeMillis(),
                160000L, 500L, 80000L, List.of());

        var chunk = new SdesChunk(0x12345678L, List.of(
                new SdesItem(SdesItem.Type.CNAME, "demo@example.com")
        ));
        var sdes = new SourceDescription(List.of(chunk));

        var compound = new CompoundPacket(List.of(sr, sdes));

        ByteBuffer encoded = RtcpCodec.encodeCompound(compound);
        LOG.info("Compound encoded: {} bytes", encoded.remaining());

        CompoundPacket decoded = RtcpCodec.decodeCompound(encoded);
        LOG.info("Compound decoded: {} packets", decoded.size());

        boolean sizeOk = decoded.size() == 2;
        boolean firstIsSR = decoded.packets().getFirst() instanceof SenderReport;
        boolean secondIsSDES = decoded.packets().get(1) instanceof SourceDescription;

        return sizeOk && firstIsSR && secondIsSDES;
    }

    // ======================== 7. JITTER BUFFER ================================

    /**
     * Demonstrates jitter buffer operations: insert, poll, duplicate detection,
     * adaptive delay.
     *
     * @since 1.0.0
     */
    static boolean demoJitterBuffer() {
        LOG.info("=== 7. Jitter Buffer ===");
        var buffer = new JitterBuffer(100, 20, 200);

        // Insert packets in order
        for (int i = 0; i < 5; i++) {
            var pkt = RtpPacket.of(96, i, i * 160L, 0x12345678L,
                    ("frame-" + i).getBytes());
            var result = buffer.insert(pkt);
            LOG.info("Insert seq={}: {}", i, result);
        }
        boolean sizeOk = buffer.size() == 5;
        LOG.info("Buffer size: {}", buffer.size());

        // Poll in sequence
        var first = buffer.poll();
        boolean pollOk = first.isPresent() && first.get().header().sequenceNumber() == 0;
        LOG.info("Polled seq: {}", first.map(p -> p.header().sequenceNumber()).orElse(-1));

        // Insert duplicate
        var dup = RtpPacket.of(96, 3, 480L, 0x12345678L, "dup".getBytes());
        var dupResult = buffer.insert(dup);
        boolean dupOk = dupResult == JitterBuffer.InsertResult.DUPLICATE;
        LOG.info("Duplicate insert: {}", dupResult);

        // Out-of-order insert
        var ooo = RtpPacket.of(96, 10, 1600L, 0x12345678L, "ooo".getBytes());
        buffer.insert(ooo);
        boolean afterInsert = buffer.size() == 5; // 4 remaining + 1 new

        // Adaptive delay
        buffer.adaptDelay(50.0);
        int delay = buffer.adaptiveDelayMs();
        boolean delayOk = delay >= 20 && delay <= 200;
        LOG.info("Adaptive delay: {} ms", delay);

        // Statistics
        LOG.info("Total received: {}, played: {}, duplicates: {}",
                buffer.totalReceived(), buffer.totalPlayed(), buffer.duplicateCount());

        return sizeOk && pollOk && dupOk && afterInsert && delayOk;
    }

    // ======================== 8. RTP SESSION =================================

    /**
     * Demonstrates RTP session management: participants, SSRC collision.
     *
     * @since 1.0.0
     */
    static boolean demoRtpSession() {
        LOG.info("=== 8. RTP Session ===");
        var session = new RtpSession("demo@example.com");

        LOG.info("Local SSRC: 0x{}", Long.toHexString(session.localSsrc()));
        LOG.info("CNAME: {}", session.cname());

        // Add remote participants
        RtpParticipant remote1 = session.getOrCreateParticipant(0xAABBCCDDL);
        remote1.setCname("remote1@example.com");
        RtpParticipant remote2 = session.getOrCreateParticipant(0x11223344L);
        remote2.setCname("remote2@example.com");

        LOG.info("Participants: {}", session.participantCount());
        boolean participantOk = session.participantCount() == 3; // local + 2 remote

        // Lookup
        var found = session.getParticipant(0xAABBCCDDL);
        boolean lookupOk = found.isPresent()
                && found.get().cname().isPresent()
                && "remote1@example.com".equals(found.get().cname().get());

        // Collision detection
        boolean collisionDetected = session.detectCollision(session.localSsrc());
        LOG.info("Collision detected: {}", collisionDetected);
        boolean collisionOk = collisionDetected && session.collisionCount() == 1;

        // Collision resolution
        long newSsrc = session.resolveCollision();
        boolean resolutionOk = newSsrc != session.localSsrc();
        LOG.info("New SSRC candidate: 0x{}", Long.toHexString(newSsrc));

        // Remove participant
        var removed = session.removeParticipant(0x11223344L);
        boolean removeOk = removed.isPresent() && session.participantCount() == 2;
        LOG.info("After remove, participants: {}", session.participantCount());

        // Cannot remove local
        var localRemove = session.removeParticipant(session.localSsrc());
        boolean localProtected = localRemove.isEmpty();

        return participantOk && lookupOk && collisionOk && resolutionOk
                && removeOk && localProtected;
    }

    // ======================== 9. RTCP INTERVAL ===============================

    /**
     * Demonstrates RTCP transmission interval calculation per RFC 3550.
     *
     * @since 1.0.0
     */
    static boolean demoRtcpInterval() {
        LOG.info("=== 9. RTCP Interval Calculation ===");
        var calc = new RtcpIntervalCalculator(64000); // 64 kbps session bandwidth

        // Deterministic interval (initial has lower minimum of 2.5s)
        double deterministic = calc.computeDeterministicInterval(10, 3, false);
        LOG.info("Deterministic interval (10 members, 3 senders): {} sec", deterministic);
        boolean deterministicOk = deterministic >= 2.5; // minimum 2.5 seconds for initial

        // Randomized interval
        double randomized = calc.computeRandomizedInterval(10, 3, false);
        LOG.info("Randomized interval: {} sec", randomized);
        boolean randomizedOk = randomized > 0;

        // Mark initial sent, then compute with full 5s minimum
        calc.markInitialSent();
        double afterInitial = calc.computeDeterministicInterval(10, 3, false);
        LOG.info("After marking initial sent: {} sec", afterInitial);
        boolean afterInitialOk = afterInitial >= 5.0;

        // Average packet size update
        calc.updateAvgPacketSize(200);
        double avgSize = calc.avgPacketSize();
        LOG.info("Average packet size after update: {} bytes", avgSize);
        boolean avgSizeOk = avgSize > 128; // should increase from 128 initial

        return deterministicOk && randomizedOk && afterInitialOk && avgSizeOk;
    }
}
