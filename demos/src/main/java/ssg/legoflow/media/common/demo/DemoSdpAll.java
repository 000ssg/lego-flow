package ssg.legoflow.media.common.demo;

import ssg.legoflow.media.common.builder.MediaBuilder;
import ssg.legoflow.media.common.builder.SessionBuilder;
import ssg.legoflow.media.common.codec.SdpNegotiator;
import ssg.legoflow.media.common.codec.SdpParser;
import ssg.legoflow.media.common.codec.SdpWriter;
import ssg.legoflow.media.common.payload.PayloadRegistry;
import ssg.legoflow.media.common.payload.PayloadType;
import ssg.legoflow.media.common.sdp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Comprehensive demo of all media-common module features.
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>SDP parsing — parse complete SDP documents into typed model</li>
 *   <li>SDP writing — serialize SessionDescription back to RFC 4566 text</li>
 *   <li>Parse-write round-trip — verify lossless serialization cycle</li>
 *   <li>Session builder — fluent API for constructing SDP sessions</li>
 *   <li>Media builder — fluent API for constructing media descriptions</li>
 *   <li>Offer/answer negotiation — RFC 3264 media capability intersection</li>
 *   <li>Payload registry — static and dynamic RTP payload type lookup</li>
 *   <li>SDP model types — Origin, Timing, Bandwidth, ConnectionInfo, Direction</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoSdpAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoSdpAll.class);

    /** Set to {@code true} to use external SDP data sources. */
    public static boolean USE_EXTERNAL = false;

    private DemoSdpAll() {}

    /**
     * Results from running the full demo.
     *
     * @param sdpParsing       true if SDP parsing produced a valid SessionDescription
     * @param sdpWriting       true if SDP writing produced valid RFC 4566 text
     * @param roundTrip        true if parse-write-parse round-trip preserved all data
     * @param sessionBuilder   true if SessionBuilder produced a valid session
     * @param mediaBuilder     true if MediaBuilder produced a valid media description
     * @param negotiation      true if offer/answer negotiation produced a valid answer
     * @param payloadRegistry  number of static payload types in the registry
     * @param modelTypes       true if all SDP model types parsed and formatted correctly
     * @since 0.1.0
     */
    public record Results(
            boolean sdpParsing,
            boolean sdpWriting,
            boolean roundTrip,
            boolean sessionBuilder,
            boolean mediaBuilder,
            boolean negotiation,
            int payloadRegistry,
            boolean modelTypes
    ) {}

    /**
     * Runs the comprehensive demo covering all media-common features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     * @since 0.1.0
     */
    public static Results runAll() throws Exception {
        boolean parsing = demoSdpParsing();
        boolean writing = demoSdpWriting();
        boolean roundTrip = demoRoundTrip();
        boolean sessionBuilder = demoSessionBuilder();
        boolean mediaBuilder = demoMediaBuilder();
        boolean negotiation = demoNegotiation();
        int payloadCount = demoPayloadRegistry();
        boolean modelTypes = demoModelTypes();

        return new Results(
                parsing, writing, roundTrip, sessionBuilder,
                mediaBuilder, negotiation, payloadCount, modelTypes
        );
    }

    // ======================== 1. SDP PARSING ================================

    /**
     * Demonstrates parsing a complete SDP document into a typed model.
     *
     * @since 0.1.0
     */
    static boolean demoSdpParsing() {
        LOG.info("=== 1. SDP Parsing ===");
        String sdp = """
                v=0\r
                o=alice 2890844526 2890842807 IN IP4 10.0.0.1\r
                s=Audio Call\r
                i=A simple audio session\r
                c=IN IP4 10.0.0.1\r
                t=0 0\r
                a=tool:Lego-Flow-SDP/1.0\r
                m=audio 49170 RTP/AVP 0 8 96\r
                a=rtpmap:0 PCMU/8000\r
                a=rtpmap:8 PCMA/8000\r
                a=rtpmap:96 opus/48000/2\r
                a=sendrecv\r
                """;

        SessionDescription session = SdpParser.parse(sdp);

        LOG.info("Version: {}", session.version());
        LOG.info("Session name: {}", session.sessionName());
        LOG.info("Origin: {}", session.origin().format());
        LOG.info("Media descriptions: {}", session.mediaDescriptions().size());

        var media = session.mediaDescriptions().getFirst();
        LOG.info("Media type: {}", media.mediaType());
        LOG.info("Port: {}", media.port());
        LOG.info("Protocol: {}", media.protocol());
        LOG.info("Formats: {}", media.formats());
        LOG.info("RTP maps: {}", media.rtpMaps().size());

        boolean hasName = "Audio Call".equals(session.sessionName());
        boolean hasOrigin = session.origin() != null;
        boolean hasMedia = session.mediaDescriptions().size() == 1;
        boolean hasFormats = media.formats().size() == 3;
        boolean hasRtpMaps = media.rtpMaps().size() == 3;
        boolean hasInfo = session.sessionInfo().isPresent();

        return hasName && hasOrigin && hasMedia && hasFormats && hasRtpMaps && hasInfo;
    }

    // ======================== 2. SDP WRITING =================================

    /**
     * Demonstrates serializing a SessionDescription to RFC 4566 text.
     *
     * @since 0.1.0
     */
    static boolean demoSdpWriting() {
        LOG.info("=== 2. SDP Writing ===");
        var audio = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .format(0)
                .format(8)
                .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                .direction(Direction.SENDRECV)
                .build();

        var session = new SessionBuilder()
                .origin("alice", 2890844526L, 2890842807L, "IN", "IP4", "10.0.0.1")
                .sessionName("Demo Session")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
                .media(audio)
                .build();

        String sdpText = SdpWriter.write(session);
        LOG.info("SDP output:\n{}", sdpText);

        boolean hasVersion = sdpText.contains("v=0\r\n");
        boolean hasOrigin = sdpText.contains("o=alice");
        boolean hasSessionName = sdpText.contains("s=Demo Session");
        boolean hasMedia = sdpText.contains("m=audio 49170 RTP/AVP");
        boolean hasRtpMap = sdpText.contains("a=rtpmap:96 opus/48000/2");
        boolean hasCrlf = sdpText.contains("\r\n");

        return hasVersion && hasOrigin && hasSessionName && hasMedia && hasRtpMap && hasCrlf;
    }

    // ======================== 3. ROUND-TRIP ===================================

    /**
     * Demonstrates lossless parse-write-parse round-trip.
     *
     * @since 0.1.0
     */
    static boolean demoRoundTrip() {
        LOG.info("=== 3. Parse-Write Round-Trip ===");
        var audio = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(0, "PCMU", 8000, 1))
                .rtpMap(RtpMap.of(8, "PCMA", 8000, 1))
                .direction(Direction.SENDRECV)
                .build();

        var original = new SessionBuilder()
                .origin("test", 1L, 1L, "IN", "IP4", "127.0.0.1")
                .sessionName("Round-Trip Test")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "127.0.0.1"))
                .media(audio)
                .build();

        // Write to SDP text
        String sdpText = SdpWriter.write(original);

        // Parse it back
        SessionDescription parsed = SdpParser.parse(sdpText);

        // Write again
        String sdpText2 = SdpWriter.write(parsed);

        LOG.info("Original SDP length: {} chars", sdpText.length());
        LOG.info("Round-trip SDP length: {} chars", sdpText2.length());

        boolean sameText = sdpText.equals(sdpText2);
        boolean sameName = original.sessionName().equals(parsed.sessionName());
        boolean sameMediaCount = original.mediaDescriptions().size() == parsed.mediaDescriptions().size();
        boolean sameFormats = original.mediaDescriptions().getFirst().formats()
                .equals(parsed.mediaDescriptions().getFirst().formats());

        LOG.info("Texts match: {}, Names match: {}", sameText, sameName);
        return sameText && sameName && sameMediaCount && sameFormats;
    }

    // ======================== 4. SESSION BUILDER =============================

    /**
     * Demonstrates the fluent SessionBuilder API.
     *
     * @since 0.1.0
     */
    static boolean demoSessionBuilder() {
        LOG.info("=== 4. Session Builder ===");
        var video = new MediaBuilder(MediaType.VIDEO, 51372, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(96, "H264", 90000))
                .formatParameters(FormatParameters.parse("96 profile-level-id=42e01f"))
                .direction(Direction.SENDRECV)
                .build();

        var session = new SessionBuilder()
                .origin("bob", 123456L, 1L, "IN", "IP4", "192.168.1.100")
                .sessionName("Video Conference")
                .sessionInfo("A test video session")
                .email("bob@example.com")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "192.168.1.100"))
                .bandwidth("AS", 2000)
                .attribute("tool", "Lego-Flow-SDP/1.0")
                .media(video)
                .build();

        LOG.info("Session: {}", session.sessionName());
        LOG.info("Origin: {}", session.origin().format());
        LOG.info("Email: {}", session.email().orElse("none"));
        LOG.info("Bandwidths: {}", session.bandwidths().size());
        LOG.info("Attributes: {}", session.attributes().size());

        boolean hasName = "Video Conference".equals(session.sessionName());
        boolean hasEmail = session.email().isPresent();
        boolean hasBandwidth = session.bandwidths().size() == 1;
        boolean hasAttribute = session.findAttribute("tool").isPresent();
        boolean hasVideo = session.mediaDescriptions().size() == 1
                && session.mediaDescriptions().getFirst().mediaType() == MediaType.VIDEO;
        boolean hasTiming = !session.timings().isEmpty();

        return hasName && hasEmail && hasBandwidth && hasAttribute && hasVideo && hasTiming;
    }

    // ======================== 5. MEDIA BUILDER ================================

    /**
     * Demonstrates the fluent MediaBuilder API with automatic attribute management.
     *
     * @since 0.1.0
     */
    static boolean demoMediaBuilder() {
        LOG.info("=== 5. Media Builder ===");
        var media = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(0, "PCMU", 8000, 1))
                .rtpMap(RtpMap.of(8, "PCMA", 8000, 1))
                .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                .formatParameters(FormatParameters.parse("96 minptime=10;useinbandfec=1"))
                .direction(Direction.SENDONLY)
                .bandwidth("AS", 128)
                .attribute("ptime", "20")
                .build();

        LOG.info("Media: {}", media.formatMediaLine());
        LOG.info("Direction: {}", media.direction());
        LOG.info("Formats: {}", media.formats());
        LOG.info("RTP maps: {}", media.rtpMaps().size());
        LOG.info("Format params: {}", media.formatParameters().size());

        boolean hasType = media.mediaType() == MediaType.AUDIO;
        boolean hasPort = media.port() == 49170;
        boolean hasProtocol = media.protocol() == TransportProtocol.RTP_AVP;
        boolean hasFormats = media.formats().size() == 3;
        boolean hasRtpMaps = media.rtpMaps().size() == 3;
        boolean hasFmtp = media.formatParameters().size() == 1;
        boolean hasDirection = media.direction() == Direction.SENDONLY;
        boolean hasBandwidth = media.bandwidths().size() == 1;

        return hasType && hasPort && hasProtocol && hasFormats
                && hasRtpMaps && hasFmtp && hasDirection && hasBandwidth;
    }

    // ======================== 6. OFFER/ANSWER NEGOTIATION ====================

    /**
     * Demonstrates RFC 3264 offer/answer negotiation.
     *
     * @since 0.1.0
     */
    static boolean demoNegotiation() {
        LOG.info("=== 6. Offer/Answer Negotiation ===");

        // Offer: audio with PCMU, PCMA, Opus
        var offerAudio = new MediaBuilder(MediaType.AUDIO, 49170, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(0, "PCMU", 8000, 1))
                .rtpMap(RtpMap.of(8, "PCMA", 8000, 1))
                .rtpMap(RtpMap.of(96, "opus", 48000, 2))
                .direction(Direction.SENDRECV)
                .build();

        var offer = new SessionBuilder()
                .origin("alice", 1L, 1L, "IN", "IP4", "10.0.0.1")
                .sessionName("Offer")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.1"))
                .media(offerAudio)
                .build();

        // Answerer capabilities: only PCMU and Opus
        var answererAudio = new MediaBuilder(MediaType.AUDIO, 40000, TransportProtocol.RTP_AVP)
                .rtpMap(RtpMap.of(0, "PCMU", 8000, 1))
                .rtpMap(RtpMap.of(97, "opus", 48000, 2))
                .direction(Direction.SENDRECV)
                .build();

        var answererCaps = new SessionBuilder()
                .origin("bob", 2L, 1L, "IN", "IP4", "10.0.0.2")
                .sessionName("Answer")
                .connectionInfo(ConnectionInfo.unicast("IN", "IP4", "10.0.0.2"))
                .media(answererAudio)
                .build();

        Optional<SessionDescription> answer = SdpNegotiator.negotiate(offer, answererCaps);

        boolean hasAnswer = answer.isPresent();
        LOG.info("Negotiation result: {}", hasAnswer ? "accepted" : "rejected");

        if (hasAnswer) {
            var answerSdp = answer.get();
            var answerMedia = answerSdp.mediaDescriptions().getFirst();
            LOG.info("Answer formats: {}", answerMedia.formats());
            LOG.info("Answer direction: {}", answerMedia.direction());
            LOG.info("Answer RTP maps: {}", answerMedia.rtpMaps().size());

            boolean hasFormats = answerMedia.formats().size() == 2; // PCMU + Opus
            boolean hasDirection = answerMedia.direction() == Direction.SENDRECV;
            return hasAnswer && hasFormats && hasDirection;
        }
        return false;
    }

    // ======================== 7. PAYLOAD REGISTRY ============================

    /**
     * Demonstrates static and dynamic payload type management.
     *
     * @since 0.1.0
     */
    static int demoPayloadRegistry() {
        LOG.info("=== 7. Payload Registry ===");
        var registry = new PayloadRegistry();

        // Static types
        var pcmu = registry.lookup(0);
        LOG.info("PT 0: {}", pcmu.map(PayloadType::codec).orElse("unknown"));

        var pcma = registry.lookup(8);
        LOG.info("PT 8: {}", pcma.map(PayloadType::codec).orElse("unknown"));

        // Dynamic registration
        registry.registerDynamic(96, "opus", 48000, OptionalInt.of(2), "audio");
        registry.registerDynamic(97, "H264", 90000, OptionalInt.empty(), "video");

        var opus = registry.lookup(96);
        LOG.info("PT 96: {}", opus.map(PayloadType::codec).orElse("unknown"));

        var h264 = registry.lookup(97);
        LOG.info("PT 97: {}", h264.map(PayloadType::codec).orElse("unknown"));

        int staticCount = PayloadRegistry.staticTypes().size();
        int dynamicCount = registry.dynamicTypes().size();
        LOG.info("Static types: {}, Dynamic types: {}", staticCount, dynamicCount);

        boolean hasPcmu = pcmu.isPresent() && "PCMU".equals(pcmu.get().codec());
        boolean hasPcma = pcma.isPresent() && "PCMA".equals(pcma.get().codec());
        boolean hasOpus = opus.isPresent() && "opus".equals(opus.get().codec());
        boolean hasH264 = h264.isPresent() && "H264".equals(h264.get().codec());
        boolean dynamicOk = dynamicCount == 2;

        return hasPcmu && hasPcma && hasOpus && hasH264 && dynamicOk ? staticCount : 0;
    }

    // ======================== 8. MODEL TYPES ==================================

    /**
     * Demonstrates SDP model types: Origin, Timing, Bandwidth, ConnectionInfo, Direction.
     *
     * @since 0.1.0
     */
    static boolean demoModelTypes() {
        LOG.info("=== 8. SDP Model Types ===");

        // Origin
        var origin = Origin.parse("alice 2890844526 2890842807 IN IP4 10.0.0.1");
        LOG.info("Origin: username={}, sessionId={}", origin.username(), origin.sessionId());
        boolean originOk = "alice".equals(origin.username()) && origin.sessionId() == 2890844526L;

        // Timing
        var timing = Timing.parse("0 0");
        LOG.info("Timing: start={}, stop={}", timing.startTime(), timing.stopTime());
        boolean timingOk = timing.startTime() == 0 && timing.stopTime() == 0;

        // Bandwidth
        var bandwidth = Bandwidth.parse("AS:128");
        LOG.info("Bandwidth: {}:{}", bandwidth.modifier(), bandwidth.value());
        boolean bwOk = "AS".equals(bandwidth.modifier()) && bandwidth.value() == 128;

        // ConnectionInfo
        var connInfo = ConnectionInfo.parse("IN IP4 224.2.36.42/127");
        LOG.info("Connection: {}", connInfo.format());
        boolean connOk = "IP4".equals(connInfo.addrType()) && connInfo.address().contains("224.2.36.42");

        // Direction
        boolean dirOk = Direction.SENDRECV.token().equals("sendrecv")
                && Direction.fromToken("recvonly") == Direction.RECVONLY;
        LOG.info("Direction sendrecv token: {}", Direction.SENDRECV.token());

        // MediaType
        boolean mtOk = MediaType.AUDIO.token().equals("audio")
                && MediaType.fromToken("video") == MediaType.VIDEO;
        LOG.info("MediaType audio token: {}", MediaType.AUDIO.token());

        // TransportProtocol
        boolean tpOk = TransportProtocol.RTP_AVP.token().equals("RTP/AVP")
                && TransportProtocol.fromToken("RTP/SAVP") == TransportProtocol.RTP_SAVP;
        LOG.info("Protocol RTP/AVP token: {}", TransportProtocol.RTP_AVP.token());

        // Attribute
        var propAttr = Attribute.property("recvonly");
        var valueAttr = Attribute.of("rtpmap", "96 opus/48000/2");
        boolean attrOk = propAttr.value().isEmpty() && valueAttr.value().isPresent();
        LOG.info("Property attr: {}, Value attr: {}", propAttr.format(), valueAttr.format());

        return originOk && timingOk && bwOk && connOk && dirOk && mtOk && tpOk && attrOk;
    }
}
