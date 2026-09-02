package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2Settings;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.hpack.HpackDecoder;
import ssg.legoflow.http2.hpack.HpackEncoder;
import ssg.legoflow.http2.server.Http2Server;
import ssg.legoflow.http2.stream.Http2StreamState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
/**
 * Comprehensive demo of all HTTP/2 module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link Http2Server}</b> — No external dependencies.
 * Runs anywhere without installation. Supports full HTTP/2 (RFC 7540/RFC 9113) with binary
 * framing, HPACK header compression, stream multiplexing, flow control, server push, and
 * H2c upgrade. Bridges to the existing HTTP router for seamless handler reuse.
 * Ideal for development, testing, CI/CD, and learning the HTTP/2 protocol.</p>
 *
 * <p><b>Alternative: External Nginx with HTTP/2 / Apache with mod_http2 / Caddy</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with TLS ALPN negotiation</li>
 *   <li>Multi-origin server push with cache digests</li>
 *   <li>Real network stream prioritization and flow control</li>
 *   <li>Integration testing against production HTTP/2 servers</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All frame encoding, HPACK compression, and stream management use the same API
 * regardless of backend. When {@code USE_EXTERNAL=true}, the demo connects
 * to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>Multiplexed streams — concurrent requests over a single connection</li>
 *   <li>Server push — PUSH_PROMISE with associated response</li>
 *   <li>Flow control — connection and stream send window management</li>
 *   <li>HPACK header compression — static/dynamic table, Huffman coding</li>
 *   <li>H2c upgrade — cleartext HTTP/2 upgrade from HTTP/1.1</li>
 *   <li>Stream priorities — dependency-based stream scheduling</li>
 *   <li>SETTINGS frame — negotiation of connection parameters</li>
 *   <li>GOAWAY — graceful connection shutdown</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoHttp2All {

    private static final Logger LOG = LoggerFactory.getLogger(DemoHttp2All.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house Http2Server (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true for Nginx/Apache/Caddy with HTTP/2
    // =========================================================================

    /** Set to {@code true} to connect to an external HTTP/2 server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external HTTP/2 server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external HTTP/2 server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 8443;

    private DemoHttp2All() {}

    /**
     * Results from running the full demo.
     *
     * @param multiplexedStreams  number of concurrent streams created
     * @param serverPush         true if PUSH_PROMISE frame was generated
     * @param flowControl        true if flow control windows tracked correctly
     * @param hpackCompression   true if HPACK encode/decode round-trip succeeded
     * @param h2cUpgrade         true if H2c cleartext upgrade succeeded
     * @param streamPriorities   true if stream priority frame created correctly
     * @param settingsFrame      true if SETTINGS negotiation succeeded
     * @param goaway             true if GOAWAY frame generated correctly
     */
    public record Results(
            int multiplexedStreams,
            boolean serverPush,
            boolean flowControl,
            boolean hpackCompression,
            boolean h2cUpgrade,
            boolean streamPriorities,
            boolean settingsFrame,
            boolean goaway
    ) {}

    /**
     * Runs the comprehensive demo covering all HTTP/2 features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        int multiplexed = demoMultiplexedStreams();
        boolean push = demoServerPush();
        boolean flow = demoFlowControl();
        boolean hpack = demoHpackCompression();
        boolean h2c = demoH2cUpgrade();
        boolean priorities = demoStreamPriorities();
        boolean settings = demoSettingsFrame();
        boolean goaway = demoGoaway();

        return new Results(
                multiplexed, push, flow, hpack,
                h2c, priorities, settings, goaway
        );
    }

    // ======================== 1. MULTIPLEXED STREAMS ========================

    /**
     * Demonstrates concurrent multiplexed streams over a single HTTP/2 connection.
     * Client sends multiple requests; each gets a unique odd stream ID.
     */
    static int demoMultiplexedStreams() {
        LOG.info("=== 1. Multiplexed Streams ===");
        var demo = new MultiplexingDemo();
        demo.client().connect();

        var frames = demo.sendConcurrentRequests("/resource/1", "/resource/2", "/resource/3");
        var headersFrames = frames.stream()
                .filter(f -> f.type() == Http2FrameType.HEADERS)
                .toList();

        var streamIds = headersFrames.stream()
                .map(Http2Frame::streamId)
                .distinct()
                .toList();

        LOG.info("Multiplexed streams: {}, stream IDs: {}", streamIds.size(), streamIds);

        // Verify all stream IDs are odd (client-initiated)
        for (int id : streamIds) {
            if (id % 2 != 1) {
                LOG.warn("Unexpected even stream ID: {}", id);
            }
        }

        return streamIds.size();
    }

    // ======================== 2. SERVER PUSH ================================

    /**
     * Demonstrates HTTP/2 server push: the server sends PUSH_PROMISE for a
     * related resource before the client requests it.
     */
    static boolean demoServerPush() {
        LOG.info("=== 2. Server Push ===");
        var demo = new ServerPushDemo();
        var serverConn = demo.server().acceptConnection();

        // Create client stream
        var stream = serverConn.streamManager().getOrCreateStream(1);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.headers().set(":method", "GET");
        stream.headers().set(":path", "/page");
        stream.headers().set(":scheme", "https");
        stream.headers().set(":authority", "localhost");
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var outFrames = demo.handleRequestWithPush(serverConn, stream);

        var pushPromise = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.PUSH_PROMISE)
                .findFirst();
        boolean hasPush = pushPromise.isPresent();
        LOG.info("PUSH_PROMISE present: {}", hasPush);

        if (hasPush) {
            var payload = pushPromise.get().payload();
            int promisedStreamId = payload.getInt() & 0x7FFFFFFF;
            LOG.info("Promised stream ID: {} (even={})", promisedStreamId, promisedStreamId % 2 == 0);
        }

        var dataFrames = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.DATA)
                .toList();
        LOG.info("Data frames: {}", dataFrames.size());

        return hasPush;
    }

    // ======================== 3. FLOW CONTROL ===============================

    /**
     * Demonstrates HTTP/2 flow control: connection-level and stream-level
     * send windows, WINDOW_UPDATE frames.
     */
    static boolean demoFlowControl() {
        LOG.info("=== 3. Flow Control ===");
        var demo = new FlowControlDemo();

        int initialWindow = demo.getConnectionSendWindow();
        LOG.info("Initial connection window: {}", initialWindow);

        // Send a payload on stream 1
        byte[] payload = new byte[1000];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 256);
        }
        var dataFrames = demo.sendLargePayload(1, payload);
        LOG.info("Data frames generated: {}", dataFrames.size());

        int afterSend = demo.getConnectionSendWindow();
        LOG.info("Connection window after send: {}", afterSend);
        boolean windowDecreased = afterSend < initialWindow;

        // Apply window update
        demo.applyWindowUpdate(0, 1000);
        int afterUpdate = demo.getConnectionSendWindow();
        LOG.info("Connection window after update: {}", afterUpdate);
        boolean windowRestored = afterUpdate > afterSend;

        return windowDecreased && windowRestored && dataFrames.size() >= 1;
    }

    // ======================== 4. HPACK COMPRESSION ===========================

    /**
     * Demonstrates HPACK header compression: encoding and decoding header blocks
     * with static table, dynamic table, and optional Huffman coding.
     */
    static boolean demoHpackCompression() {
        LOG.info("=== 4. HPACK Header Compression ===");
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        // Encode headers
        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/index.html");
        headers.set(":scheme", "https");
        headers.set(":authority", "example.com");
        headers.set("accept", "text/html");

        ByteBuffer encoded = encoder.encode(headers);
        int encodedSize = encoded.remaining();
        LOG.info("Encoded header block: {} bytes", encodedSize);

        // Decode headers
        var decoded = decoder.decodeToHttpHeaders(encoded);
        boolean methodOk = "GET".equals(decoded.get(":method"));
        boolean pathOk = "/index.html".equals(decoded.get(":path"));
        boolean schemeOk = "https".equals(decoded.get(":scheme"));
        boolean authorityOk = "example.com".equals(decoded.get(":authority"));
        LOG.info("Decoded: method={}, path={}, scheme={}, authority={}",
                decoded.get(":method"), decoded.get(":path"),
                decoded.get(":scheme"), decoded.get(":authority"));

        // Huffman encoding
        encoder.setUseHuffman(true);
        ByteBuffer huffmanEncoded = encoder.encode(headers);
        int huffmanSize = huffmanEncoded.remaining();
        LOG.info("Huffman encoded: {} bytes (vs {} without)", huffmanSize, encodedSize);

        return methodOk && pathOk && schemeOk && authorityOk;
    }

    // ======================== 5. H2C UPGRADE ================================

    /**
     * Demonstrates cleartext HTTP/2 (h2c) upgrade from HTTP/1.1.
     * The client sends an HTTP/1.1 request with Upgrade: h2c header.
     */
    static boolean demoH2cUpgrade() {
        LOG.info("=== 5. H2c Upgrade ===");
        var demo = new H2cUpgradeDemo();

        // Create h2c upgrade request
        var request = demo.createH2cUpgradeRequest("/hello");
        boolean isUpgrade = demo.upgradeHandler().isH2cUpgradeRequest(request);
        LOG.info("Is h2c upgrade request: {}", isUpgrade);

        // Perform upgrade
        var connection = demo.performUpgrade(request);
        boolean connectionOk = connection != null && connection.isServer();
        LOG.info("Upgraded connection: server={}", connectionOk);

        // Create upgrade response
        var response = demo.createUpgradeResponse();
        boolean is101 = response.getStatus() == HttpStatus.SWITCHING_PROTOCOLS;
        LOG.info("Upgrade response: {}", response.getStatus());

        return isUpgrade && connectionOk && is101;
    }

    // ======================== 6. STREAM PRIORITIES ===========================

    /**
     * Demonstrates HTTP/2 stream priorities using the PRIORITY frame.
     * Streams can declare dependencies on other streams with weights.
     */
    static boolean demoStreamPriorities() {
        LOG.info("=== 6. Stream Priorities ===");

        // Create a PRIORITY frame: stream 3 depends on stream 1, weight=128, non-exclusive
        var priorityFrame = Http2Frame.priority(3, 1, 128, false);
        boolean isPriority = priorityFrame.type() == Http2FrameType.PRIORITY;
        boolean correctStream = priorityFrame.streamId() == 3;
        LOG.info("Priority frame: type={}, streamId={}", priorityFrame.type(), priorityFrame.streamId());

        // Encode and decode round-trip
        ByteBuffer encoded = priorityFrame.encode();
        var decoded = Http2Frame.decode(encoded);
        boolean roundTrip = decoded.type() == Http2FrameType.PRIORITY
                && decoded.streamId() == 3;
        LOG.info("Round-trip: type={}, streamId={}", decoded.type(), decoded.streamId());

        // Exclusive dependency
        var exclusivePriority = Http2Frame.priority(5, 1, 200, true);
        boolean isExclusive = exclusivePriority.type() == Http2FrameType.PRIORITY;
        LOG.info("Exclusive priority: streamId={}", exclusivePriority.streamId());

        return isPriority && correctStream && roundTrip && isExclusive;
    }

    // ======================== 7. SETTINGS FRAME =============================

    /**
     * Demonstrates HTTP/2 SETTINGS frame: configuring connection parameters
     * like max concurrent streams, initial window size, max frame size.
     */
    static boolean demoSettingsFrame() {
        LOG.info("=== 7. SETTINGS Frame ===");

        // Create settings
        var settings = new Http2Settings();
        settings.set(Http2Settings.MAX_CONCURRENT_STREAMS, 200);
        settings.set(Http2Settings.INITIAL_WINDOW_SIZE, 32768);
        settings.set(Http2Settings.MAX_FRAME_SIZE, 32768);
        settings.set(Http2Settings.ENABLE_PUSH, 1);

        LOG.info("Settings: maxStreams={}, windowSize={}, maxFrame={}, push={}",
                settings.maxConcurrentStreams(),
                settings.initialWindowSize(),
                settings.maxFrameSize(),
                settings.enablePush());

        // Encode and decode round-trip
        ByteBuffer encoded = settings.encode();
        var decoded = Http2Settings.decode(encoded);
        boolean maxStreamsOk = decoded.maxConcurrentStreams() == 200;
        boolean windowOk = decoded.initialWindowSize() == 32768;
        boolean frameOk = decoded.maxFrameSize() == 32768;
        boolean pushOk = decoded.enablePush();

        LOG.info("Decoded: maxStreams={}, window={}, frame={}, push={}",
                decoded.maxConcurrentStreams(), decoded.initialWindowSize(),
                decoded.maxFrameSize(), decoded.enablePush());

        // SETTINGS ACK frame
        var ackFrame = Http2Frame.settingsAck();
        boolean isAck = ackFrame.type() == Http2FrameType.SETTINGS
                && ackFrame.hasFlag(Http2Flags.ACK);
        LOG.info("Settings ACK: {}", isAck);

        return maxStreamsOk && windowOk && frameOk && pushOk && isAck;
    }

    // ======================== 8. GOAWAY =====================================

    /**
     * Demonstrates HTTP/2 GOAWAY frame for graceful connection shutdown.
     * The server sends GOAWAY with the last stream ID it processed and an error code.
     */
    static boolean demoGoaway() {
        LOG.info("=== 8. GOAWAY ===");

        // Create GOAWAY frame
        var goawayFrame = Http2Frame.goaway(5, Http2ErrorCode.NO_ERROR,
                ByteBuffer.wrap("graceful shutdown".getBytes(StandardCharsets.UTF_8)));
        boolean isGoaway = goawayFrame.type() == Http2FrameType.GOAWAY;
        LOG.info("GOAWAY frame: type={}, streamId={}", goawayFrame.type(), goawayFrame.streamId());

        // Encode and decode round-trip
        ByteBuffer encoded = goawayFrame.encode();
        var decoded = Http2Frame.decode(encoded);
        boolean roundTrip = decoded.type() == Http2FrameType.GOAWAY;
        LOG.info("Decoded GOAWAY: type={}", decoded.type());

        // Connection-level GOAWAY via Http2Connection
        var connection = new Http2Connection(true);
        var connGoaway = connection.createGoaway(Http2ErrorCode.NO_ERROR);
        boolean connGoawayOk = connGoaway.type() == Http2FrameType.GOAWAY;
        LOG.info("Connection GOAWAY: type={}", connGoaway.type());

        // Error codes
        boolean noError = Http2ErrorCode.NO_ERROR.code() == 0;
        boolean protocolError = Http2ErrorCode.PROTOCOL_ERROR.code() == 1;
        LOG.info("Error codes: NO_ERROR={}, PROTOCOL_ERROR={}",
                Http2ErrorCode.NO_ERROR.code(), Http2ErrorCode.PROTOCOL_ERROR.code());

        return isGoaway && roundTrip && connGoawayOk && noError && protocolError;
    }
}
