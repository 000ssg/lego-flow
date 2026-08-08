package ssg.legoflow.media.rtsp.demo;

import ssg.legoflow.media.common.sdp.*;
import ssg.legoflow.media.rtsp.client.RtspClient;
import ssg.legoflow.media.rtsp.protocol.*;
import ssg.legoflow.media.rtsp.server.MediaSource;
import ssg.legoflow.media.rtsp.server.RtspServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Comprehensive demo of all RTSP module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link RtspServer}</b> — No external dependencies.
 * Runs anywhere without installation. Supports RTSP 2.0 (RFC 7826) with full session
 * management, all standard methods (OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN,
 * GET_PARAMETER, SET_PARAMETER, RECORD), SDP session description, and RTP-over-TCP
 * interleaved transport. Ideal for development, testing, CI/CD, and learning the
 * RTSP protocol.</p>
 *
 * <p><b>Alternative: External VLC / FFmpeg / GStreamer / Wowza</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production streaming with actual RTP/RTCP media transport</li>
 *   <li>Hardware transcoding and adaptive bitrate streaming</li>
 *   <li>Multi-client concurrent streaming with real media content</li>
 *   <li>Integration testing against production media servers</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code uses the same RTSP request/response API regardless of backend.
 * When {@code USE_EXTERNAL=true}, the demo uses raw request/response handling
 * against the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>DESCRIBE — SDP session description with media details</li>
 *   <li>SETUP — transport negotiation (TCP/UDP interleaved)</li>
 *   <li>PLAY — start playback with optional range</li>
 *   <li>PAUSE — suspend playback</li>
 *   <li>TEARDOWN — terminate session</li>
 *   <li>GET_PARAMETER — keep-alive and parameter query</li>
 *   <li>SET_PARAMETER — parameter modification</li>
 *   <li>SDP session description — media type, codec, connection info</li>
 *   <li>Full playback workflow — OPTIONS through TEARDOWN sequence</li>
 *   <li>Session management — session ID tracking, CSeq numbering</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoRtspAll {

    private static final Logger LOG = LoggerFactory.getLogger(DemoRtspAll.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house RtspServer (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for VLC/FFmpeg
    // =========================================================================

    /** Set to {@code true} to connect to an external RTSP server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external RTSP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external RTSP server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 8554;

    private DemoRtspAll() {}

    /**
     * Results from running the full demo.
     *
     * @param describe          true if DESCRIBE returned valid SDP
     * @param setup             true if SETUP negotiated transport successfully
     * @param playPause         true if PLAY and PAUSE returned 200 OK
     * @param teardown          true if TEARDOWN returned 200 OK
     * @param getParameter      true if GET_PARAMETER returned 200 OK
     * @param setParameter      true if SET_PARAMETER returned 200 OK
     * @param sdpDescription    true if SDP contained valid media description
     * @param fullWorkflow      true if complete OPTIONS-to-TEARDOWN sequence succeeded
     * @param sessionManagement true if session IDs and CSeq were tracked correctly
     */
    public record Results(
            boolean describe,
            boolean setup,
            boolean playPause,
            boolean teardown,
            boolean getParameter,
            boolean setParameter,
            boolean sdpDescription,
            boolean fullWorkflow,
            boolean sessionManagement
    ) {}

    /**
     * Runs the comprehensive demo covering all RTSP features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        var server = StreamingServerDemo.createServer(EXTERNAL_PORT);
        try {
            String uri = "rtsp://localhost:" + EXTERNAL_PORT + "/test/stream";

            // 1. DESCRIBE
            boolean describeOk = demoDescribe(server, uri);

            // 2. SETUP
            boolean setupOk = demoSetup(server, uri);

            // 3. PLAY / PAUSE
            boolean playPauseOk = demoPlayPause(server, uri);

            // 4. TEARDOWN
            boolean teardownOk = demoTeardown(server, uri);

            // 5. GET_PARAMETER
            boolean getParamOk = demoGetParameter(server, uri);

            // 6. SET_PARAMETER
            boolean setParamOk = demoSetParameter(server, uri);

            // 7. SDP session description
            boolean sdpOk = demoSdpDescription();

            // 8. Full playback workflow
            boolean workflowOk = demoFullWorkflow(server, uri);

            // 9. Session management
            boolean sessionOk = demoSessionManagement(server, uri);

            return new Results(
                    describeOk, setupOk, playPauseOk, teardownOk,
                    getParamOk, setParamOk, sdpOk, workflowOk, sessionOk
            );
        } finally {
            server.close();
        }
    }

    // ======================== 1. DESCRIBE ====================================

    /**
     * Demonstrates DESCRIBE to retrieve SDP session description from the server.
     */
    static boolean demoDescribe(RtspServer server, String uri) {
        LOG.info("=== 1. DESCRIBE ===");
        var response = server.handleRequest(
                RtspRequest.builder(RtspMethod.DESCRIBE, uri)
                        .cseq(1).accept("application/sdp").build());
        LOG.info("DESCRIBE status: {}", response.status());
        LOG.info("Body length: {} bytes", response.body().length);
        return response.status() == RtspStatus.OK && response.body().length > 0;
    }

    // ======================== 2. SETUP =======================================

    /**
     * Demonstrates SETUP to negotiate transport parameters for media delivery.
     * Preferred: RTP/AVP unicast — standard UDP-based RTP delivery.
     * Alternative: RTP/AVP/TCP interleaved — for NAT traversal and firewall-friendly setups.
     */
    static boolean demoSetup(RtspServer server, String uri) {
        LOG.info("=== 2. SETUP ===");
        var response = server.handleRequest(
                RtspRequest.builder(RtspMethod.SETUP, uri)
                        .cseq(1).transport("RTP/AVP;unicast;client_port=8000-8001").build());
        LOG.info("SETUP status: {}", response.status());
        boolean hasSession = response.headers().sessionId().isPresent();
        LOG.info("Session ID: {}", response.headers().sessionId().orElse("none"));
        return response.status() == RtspStatus.OK && hasSession;
    }

    // ======================== 3. PLAY / PAUSE ================================

    /**
     * Demonstrates PLAY to start playback and PAUSE to suspend it.
     */
    static boolean demoPlayPause(RtspServer server, String uri) {
        LOG.info("=== 3. PLAY / PAUSE ===");
        // Setup first
        var setupResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.SETUP, uri)
                        .cseq(1).transport("RTP/AVP;unicast;client_port=8000-8001").build());
        String sessionId = setupResp.headers().sessionId().orElseThrow();

        // PLAY
        var playResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.PLAY, uri)
                        .cseq(2).session(sessionId).range("npt=0-").build());
        LOG.info("PLAY status: {}", playResp.status());

        // PAUSE
        var pauseResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.PAUSE, uri)
                        .cseq(3).session(sessionId).build());
        LOG.info("PAUSE status: {}", pauseResp.status());

        return playResp.status() == RtspStatus.OK && pauseResp.status() == RtspStatus.OK;
    }

    // ======================== 4. TEARDOWN ====================================

    /**
     * Demonstrates TEARDOWN to terminate an RTSP session and release resources.
     */
    static boolean demoTeardown(RtspServer server, String uri) {
        LOG.info("=== 4. TEARDOWN ===");
        // Setup first
        var setupResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.SETUP, uri)
                        .cseq(1).transport("RTP/AVP;unicast;client_port=8000-8001").build());
        String sessionId = setupResp.headers().sessionId().orElseThrow();

        var teardownResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.TEARDOWN, uri)
                        .cseq(2).session(sessionId).build());
        LOG.info("TEARDOWN status: {}", teardownResp.status());
        return teardownResp.status() == RtspStatus.OK;
    }

    // ======================== 5. GET_PARAMETER ===============================

    /**
     * Demonstrates GET_PARAMETER as a session keep-alive mechanism.
     */
    static boolean demoGetParameter(RtspServer server, String uri) {
        LOG.info("=== 5. GET_PARAMETER ===");
        var setupResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.SETUP, uri)
                        .cseq(1).transport("RTP/AVP;unicast;client_port=8000-8001").build());
        String sessionId = setupResp.headers().sessionId().orElseThrow();

        var getParamResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.GET_PARAMETER, uri)
                        .cseq(2).session(sessionId).build());
        LOG.info("GET_PARAMETER status: {}", getParamResp.status());

        // Teardown
        server.handleRequest(
                RtspRequest.builder(RtspMethod.TEARDOWN, uri)
                        .cseq(3).session(sessionId).build());
        return getParamResp.status() == RtspStatus.OK;
    }

    // ======================== 6. SET_PARAMETER ===============================

    /**
     * Demonstrates SET_PARAMETER to modify server-side parameters.
     */
    static boolean demoSetParameter(RtspServer server, String uri) {
        LOG.info("=== 6. SET_PARAMETER ===");
        var setupResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.SETUP, uri)
                        .cseq(1).transport("RTP/AVP;unicast;client_port=8000-8001").build());
        String sessionId = setupResp.headers().sessionId().orElseThrow();

        var setParamResp = server.handleRequest(
                RtspRequest.builder(RtspMethod.SET_PARAMETER, uri)
                        .cseq(2).session(sessionId).body("parameter: value").build());
        LOG.info("SET_PARAMETER status: {}", setParamResp.status());

        // Teardown
        server.handleRequest(
                RtspRequest.builder(RtspMethod.TEARDOWN, uri)
                        .cseq(3).session(sessionId).build());
        return setParamResp.status() == RtspStatus.OK;
    }

    // ======================== 7. SDP SESSION DESCRIPTION =====================

    /**
     * Demonstrates SDP session description parsing and creation.
     * SDP carries media type, codec parameters, and connection information.
     */
    static boolean demoSdpDescription() {
        LOG.info("=== 7. SDP Session Description ===");
        var source = new StreamingServerDemo.TestMediaSource();
        var sdp = source.describe();

        LOG.info("Session name: {}", sdp.sessionName());
        LOG.info("Media descriptions: {}", sdp.mediaDescriptions().size());

        var media = sdp.mediaDescriptions().getFirst();
        LOG.info("Media type: {}", media.mediaType());
        LOG.info("RTP maps: {}", media.rtpMaps().size());

        var rtpMap = media.rtpMaps().getFirst();
        LOG.info("Codec: {} @ {} Hz", rtpMap.codec(), rtpMap.clockRate());

        boolean hasName = "Test Stream".equals(sdp.sessionName());
        boolean hasMedia = sdp.mediaDescriptions().size() == 1;
        boolean hasCodec = "H264".equals(rtpMap.codec());
        boolean hasClockRate = rtpMap.clockRate() == 90000;

        return hasName && hasMedia && hasCodec && hasClockRate;
    }

    // ======================== 8. FULL PLAYBACK WORKFLOW ======================

    /**
     * Demonstrates the complete RTSP playback workflow using the high-level RtspClient.
     * Sequence: OPTIONS -> DESCRIBE -> SETUP -> PLAY -> PAUSE -> PLAY (resume) -> TEARDOWN
     */
    static boolean demoFullWorkflow(RtspServer server, String uri) {
        LOG.info("=== 8. Full Playback Workflow ===");
        try (var client = new RtspClient(uri, server)) {
            // OPTIONS
            var optionsResp = client.options();
            LOG.info("OPTIONS: {}", optionsResp.status());

            // DESCRIBE
            var describeResp = client.describe();
            LOG.info("DESCRIBE: {}", describeResp.status());

            // SETUP
            var setupResult = client.setup("RTP/AVP;unicast;client_port=8000-8001");
            LOG.info("SETUP: session={}", setupResult.sessionId());

            // PLAY
            var playResp = client.play();
            LOG.info("PLAY: {}", playResp.status());

            // PAUSE
            var pauseResp = client.pause();
            LOG.info("PAUSE: {}", pauseResp.status());

            // PLAY (resume from position)
            var resumeResp = client.play("npt=10-");
            LOG.info("PLAY (resume): {}", resumeResp.status());

            // TEARDOWN
            var teardownResp = client.teardown();
            LOG.info("TEARDOWN: {}", teardownResp.status());

            return optionsResp.status() == RtspStatus.OK
                    && describeResp.status() == RtspStatus.OK
                    && playResp.status() == RtspStatus.OK
                    && pauseResp.status() == RtspStatus.OK
                    && resumeResp.status() == RtspStatus.OK
                    && teardownResp.status() == RtspStatus.OK;
        }
    }

    // ======================== 9. SESSION MANAGEMENT ==========================

    /**
     * Demonstrates session management: session ID tracking, CSeq numbering,
     * and session lifecycle (creation, keep-alive, termination).
     */
    static boolean demoSessionManagement(RtspServer server, String uri) {
        LOG.info("=== 9. Session Management ===");
        try (var client = new RtspClient(uri, server)) {
            // Before setup: no session
            boolean noSession = client.session().isEmpty();
            LOG.info("Before setup: session={}", client.session());

            client.options();
            client.describe();
            var setupResult = client.setup("RTP/AVP;unicast;client_port=8000-8001");

            // After setup: session established
            boolean hasSession = client.session().isPresent();
            String sessionId = setupResult.sessionId();
            LOG.info("After setup: session={}, id={}", hasSession, sessionId);

            // CSeq increments
            int cseq = client.currentCseq();
            LOG.info("Current CSeq: {}", cseq);
            boolean cseqIncremented = cseq >= 3; // OPTIONS, DESCRIBE, SETUP

            // GET_PARAMETER as keep-alive
            var keepAlive = client.getParameter();
            LOG.info("Keep-alive: {}", keepAlive.status());

            // Teardown terminates session
            client.teardown();

            return noSession && hasSession && cseqIncremented
                    && sessionId != null && !sessionId.isEmpty();
        }
    }
}
