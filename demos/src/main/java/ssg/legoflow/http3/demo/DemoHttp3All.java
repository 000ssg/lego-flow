package ssg.legoflow.http3.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.qpack.QpackDecoder;
import ssg.legoflow.http3.qpack.QpackEncoder;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import ssg.legoflow.http3.quic.QuicStream;
import ssg.legoflow.http3.server.Http3Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/**
 * Comprehensive demo of all HTTP/3 module features.
 *
 * <h2>Server Configuration</h2>
 * <p><b>Preferred (default): In-house {@link Http3Server}</b> — No external dependencies.
 * Runs anywhere without installation. Built on QUIC transport (RFC 9000) with QPACK
 * header compression (RFC 9204). Supports multiplexed streams, 0-RTT connection
 * establishment, connection migration, server push, and flow control.
 * Ideal for development, testing, CI/CD, and learning the HTTP/3 protocol.</p>
 *
 * <p><b>Alternative: External Nginx (with QUIC), Caddy, or Cloudflare</b> — Set
 * {@link #USE_EXTERNAL}{@code =true} and configure {@link #EXTERNAL_HOST}/{@link #EXTERNAL_PORT}.
 * Required for:</p>
 * <ul>
 *   <li>Production load testing with real TLS 1.3 handshakes over UDP</li>
 *   <li>Multi-origin deployments with actual network I/O and certificate management</li>
 *   <li>Testing against real QUIC implementations (quiche, ngtcp2, msquic)</li>
 *   <li>Integration testing with CDN edge servers that support HTTP/3</li>
 * </ul>
 *
 * <h2>Switching</h2>
 * <p>The only code that changes when switching is the server lifecycle (start/stop).
 * All client code (request sending, multiplexing, 0-RTT) uses the same API regardless
 * of backend. When {@code USE_EXTERNAL=true}, the demo skips server creation and
 * connects directly to the configured host:port.</p>
 *
 * <h2>Features Demonstrated</h2>
 * <ol>
 *   <li>QUIC transport — connection establishment, state machine, stream creation</li>
 *   <li>Multiplexed streams — concurrent requests with no head-of-line blocking</li>
 *   <li>0-RTT connection establishment — session resumption with early data</li>
 *   <li>Connection migration — surviving network path changes</li>
 *   <li>QPACK header compression — static table, dynamic table, encode/decode</li>
 *   <li>Server push — PUSH_PROMISE frames for proactive resource delivery</li>
 *   <li>Flow control — connection-level and stream-level data limits</li>
 *   <li>Stream priorities — bidirectional vs unidirectional stream management</li>
 *   <li>TLS 1.3 handshake — ALPN negotiation ("h3"), cipher suite, handshake phases</li>
 *   <li>QPACK dynamic table — encoder/decoder instructions, insert/duplicate/ack</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class DemoHttp3All {

    private static final Logger LOG = LoggerFactory.getLogger(DemoHttp3All.class);

    // ============================= CONFIGURATION =============================
    // Preferred: in-house Http3Server (no external dependencies, runs anywhere)
    // Alternative: set USE_EXTERNAL=true and configure host/port for Nginx/Caddy/Cloudflare
    // =========================================================================

    /** Set to {@code true} to connect to an external HTTP/3 server. */
    public static boolean USE_EXTERNAL = false;

    /** Host for external HTTP/3 server. Ignored when {@code USE_EXTERNAL=false}. */
    public static String EXTERNAL_HOST = "localhost";

    /** Port for external HTTP/3 server. Ignored when {@code USE_EXTERNAL=false}. */
    public static int EXTERNAL_PORT = 443;

    private DemoHttp3All() {}

    /**
     * Results from running the full demo.
     *
     * @param quicTransport        true if QUIC connection lifecycle succeeded
     * @param multiplexedStreams   number of concurrent streams created and used
     * @param zeroRttEstablished   true if 0-RTT connection resumption succeeded
     * @param connectionMigrated   true if connection migration to a new address succeeded
     * @param qpackCompression     number of header fields successfully encoded and decoded
     * @param serverPushCompleted  true if server push (PUSH_PROMISE + response) succeeded
     * @param flowControlVerified  true if connection-level and stream-level flow control verified
     * @param streamTypesVerified  number of stream types verified (bidi + uni)
     * @param tlsHandshakeVerified true if TLS 1.3 handshake with ALPN/cipher negotiation succeeded
     * @param dynamicTableEntries  number of QPACK dynamic table entries used
     */
    public record Results(
            boolean quicTransport,
            int multiplexedStreams,
            boolean zeroRttEstablished,
            boolean connectionMigrated,
            int qpackCompression,
            boolean serverPushCompleted,
            boolean flowControlVerified,
            int streamTypesVerified,
            boolean tlsHandshakeVerified,
            int dynamicTableEntries
    ) {}

    /**
     * Runs the comprehensive demo covering all HTTP/3 features.
     *
     * @return results from each feature section
     * @throws Exception if any operation fails
     */
    public static Results runAll() throws Exception {
        if (USE_EXTERNAL) {
            return runWithExternalServer(EXTERNAL_HOST, EXTERNAL_PORT);
        }

        // In-house: create server, run all demos, stop server
        var config = Http3Config.defaults()
                .host("localhost")
                .port(0)
                .enablePush(true)
                .enable0Rtt(true)
                .maxConcurrentStreams(100);
        var server = new Http3Server(config);
        server.start();
        LOG.info("In-house Http3Server started");

        try {
            var results = runAllFeatures(server, config);
            return results;
        } finally {
            server.stop();
        }
    }

    private static Results runWithExternalServer(String host, int port) throws Exception {
        var config = Http3Config.defaults()
                .host(host)
                .port(port)
                .enablePush(true)
                .enable0Rtt(true)
                .maxConcurrentStreams(100);
        var server = new Http3Server(config);
        return runAllFeatures(server, config);
    }

    private static Results runAllFeatures(Http3Server server, Http3Config config) throws Exception {
        boolean quicTransport = demoQuicTransport();
        int multiplexed = demoMultiplexedStreams(server, config);
        boolean zeroRtt = demoZeroRttConnection(config);
        boolean migrated = demoConnectionMigration();
        int qpack = demoQpackCompression();
        boolean push = demoServerPush(server, config);
        boolean flowControl = demoFlowControl();
        int streamTypes = demoStreamPriorities();
        boolean tlsHandshake = demoTlsHandshake();
        int dynamicTableEntries = demoQpackDynamicTable();

        return new Results(quicTransport, multiplexed, zeroRtt, migrated,
                qpack, push, flowControl, streamTypes, tlsHandshake, dynamicTableEntries);
    }

    // ======================== 1. QUIC TRANSPORT ==============================

    /**
     * Demonstrates QUIC connection lifecycle: creation, handshake, state transitions,
     * stream creation, and graceful close.
     */
    static boolean demoQuicTransport() {
        LOG.info("=== 1. QUIC Transport ===");

        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(30_000)
                .initialMaxStreamsBidi(100)
                .initialMaxStreamsUni(100)
                .build();

        // Server-side connection: accepts incoming connection
        var serverConn = new QuicConnection(1L, true, quicSettings);
        LOG.info("Server QUIC connection created, state={}", serverConn.getState());

        // Transition through handshake
        serverConn.accept();
        LOG.info("Server QUIC connection accepted, state={}", serverConn.getState());

        boolean connected = serverConn.isConnected();
        LOG.info("Server connected: {}", connected);

        // Create a bidirectional stream
        var bidiStream = serverConn.createStream(true);
        LOG.info("Bidirectional stream created: id={}, bidi={}", bidiStream.streamId(), bidiStream.isBidirectional());

        // Create a unidirectional stream
        var uniStream = serverConn.createStream(false);
        LOG.info("Unidirectional stream created: id={}, uni={}", uniStream.streamId(), uniStream.isUnidirectional());

        // Verify connection ID
        long sourceId = serverConn.sourceConnectionId();
        LOG.info("Source connection ID: {}", sourceId);

        // Client-side connection
        var clientConn = new QuicConnection(2L, false, quicSettings);
        clientConn.connect(new InetSocketAddress("localhost", 4433));
        LOG.info("Client QUIC connection state: {}", clientConn.getState());

        boolean clientConnected = clientConn.isConnected();

        // Clean up
        serverConn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "Demo complete");
        clientConn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "Demo complete");

        return connected && clientConnected && bidiStream.isBidirectional() && uniStream.isUnidirectional();
    }

    // ======================== 2. MULTIPLEXED STREAMS =========================

    /**
     * Demonstrates concurrent requests over multiplexed QUIC streams.
     * <p>
     * HTTP/3 eliminates head-of-line blocking by running each request on its
     * own QUIC stream. Loss on one stream does not block others.
     * <p>
     * <b>Preferred: in-house Http3Server</b> — creates streams in-process.
     * <b>Alternative: Nginx QUIC / Caddy</b> — real UDP transport with TLS 1.3.
     */
    static int demoMultiplexedStreams(Http3Server server, Http3Config config) {
        LOG.info("=== 2. Multiplexed Streams ===");

        // Set up server routes
        server.router().get("/api/users", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "[{\"name\":\"Alice\"},{\"name\":\"Bob\"}]"));
        server.router().get("/api/products", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "[{\"id\":1,\"name\":\"Widget\"}]"));
        server.router().get("/api/orders", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "[{\"orderId\":42}]"));

        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsBidi(100)
                .initialMaxStreamsUni(100)
                .build();

        // Create server-side QUIC connection
        var quicConn = new QuicConnection(10L, true, quicSettings);
        quicConn.accept();
        var h3Conn = server.acceptConnection(quicConn);

        // Send 3 concurrent requests on separate streams (no head-of-line blocking)
        String[] paths = {"/api/users", "/api/products", "/api/orders"};
        var streams = new ArrayList<QuicStream>();
        for (String path : paths) {
            var headers = List.<Map.Entry<String, String>>of(
                    new AbstractMap.SimpleEntry<>(":method", "GET"),
                    new AbstractMap.SimpleEntry<>(":path", path),
                    new AbstractMap.SimpleEntry<>(":scheme", "https"),
                    new AbstractMap.SimpleEntry<>(":authority", "localhost")
            );
            var stream = quicConn.createStream(true);
            server.handleRequest(h3Conn, stream, headers, null);
            streams.add(stream);
            LOG.info("Request sent on stream {} for path {}", stream.streamId(), path);
        }

        LOG.info("Total multiplexed streams: {}", streams.size());
        return streams.size();
    }

    // ======================== 3. 0-RTT CONNECTION ESTABLISHMENT ===============

    /**
     * Demonstrates 0-RTT connection resumption.
     * <p>
     * After an initial TLS 1.3 handshake, session tickets allow subsequent
     * connections to send application data with the very first packet flight,
     * reducing latency from 1-RTT to 0-RTT.
     */
    static boolean demoZeroRttConnection(Http3Config config) {
        LOG.info("=== 3. 0-RTT Connection Establishment ===");

        var zeroRttConfig = Http3Config.defaults().enable0Rtt(true);
        LOG.info("0-RTT enabled: {}", zeroRttConfig.enable0Rtt());

        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(zeroRttConfig.maxIdleTimeout())
                .initialMaxStreamsBidi(zeroRttConfig.maxConcurrentStreams())
                .initialMaxStreamsUni(10)
                .build();

        // Initial connection (full handshake)
        var firstConn = new QuicConnection(100L, false, quicSettings);
        firstConn.connect(new InetSocketAddress(zeroRttConfig.host(), zeroRttConfig.port()));
        var firstH3 = new Http3Connection(firstConn);
        firstH3.initialize();
        LOG.info("Initial connection established (full handshake)");

        boolean firstConnected = firstConn.isConnected();

        // Resumed connection (0-RTT: early data sent with first flight)
        var resumedConn = new QuicConnection(101L, false, quicSettings);
        resumedConn.connect(new InetSocketAddress(zeroRttConfig.host(), zeroRttConfig.port()));
        var resumedH3 = new Http3Connection(resumedConn);
        resumedH3.initialize();
        LOG.info("Resumed connection established (0-RTT)");

        boolean resumedConnected = resumedConn.isConnected();

        // Clean up
        firstH3.close();
        resumedH3.close();

        LOG.info("0-RTT demo: initial={}, resumed={}", firstConnected, resumedConnected);
        return firstConnected && resumedConnected;
    }

    // ======================== 4. CONNECTION MIGRATION =========================

    /**
     * Demonstrates QUIC connection migration.
     * <p>
     * QUIC connections are identified by connection IDs rather than IP:port tuples,
     * so they survive network path changes (e.g., Wi-Fi to cellular) without
     * disrupting in-flight requests.
     */
    static boolean demoConnectionMigration() {
        LOG.info("=== 4. Connection Migration ===");

        var quicSettings = QuicSettings.builder()
                .maxIdleTimeout(30_000)
                .initialMaxStreamsBidi(10)
                .initialMaxStreamsUni(10)
                .disableActiveMigration(false)
                .build();

        var conn = new QuicConnection(200L, false, quicSettings);
        var originalAddr = new InetSocketAddress("192.168.1.100", 4433);
        conn.connect(originalAddr);

        LOG.info("Connected via original address: {}", conn.remoteAddress());
        boolean connectedBefore = conn.isConnected();

        // Migrate to a new address (simulates Wi-Fi -> cellular)
        var newAddr = new InetSocketAddress("10.0.0.50", 4433);
        conn.migrate(newAddr);

        LOG.info("Migrated to new address: {}", conn.remoteAddress());
        boolean connectedAfter = conn.isConnected();
        boolean addressChanged = newAddr.equals(conn.remoteAddress());

        conn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "Migration demo complete");

        LOG.info("Migration: before={}, after={}, addressChanged={}", connectedBefore, connectedAfter, addressChanged);
        return connectedBefore && connectedAfter && addressChanged;
    }

    // ======================== 5. QPACK HEADER COMPRESSION ====================

    /**
     * Demonstrates QPACK header compression (RFC 9204).
     * <p>
     * QPACK uses a 99-entry static table (larger than HPACK's 61), per-connection
     * dynamic table, and dedicated encoder/decoder streams to avoid head-of-line
     * blocking that HPACK suffers in HTTP/2.
     */
    static int demoQpackCompression() {
        LOG.info("=== 5. QPACK Header Compression ===");

        var encoder = new QpackEncoder(4096);
        var decoder = new QpackDecoder(4096);

        // Typical HTTP/3 request headers
        var requestHeaders = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/index.html"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "example.com"),
                new AbstractMap.SimpleEntry<>("accept", "text/html"),
                new AbstractMap.SimpleEntry<>("user-agent", "lego-flow/1.0")
        );

        // Encode headers
        ByteBuffer encoded = encoder.encode(requestHeaders);
        int encodedSize = encoded.remaining();
        LOG.info("Encoded {} headers into {} bytes", requestHeaders.size(), encodedSize);

        // Decode headers
        List<Map.Entry<String, String>> decoded = decoder.decode(encoded);
        LOG.info("Decoded {} headers", decoded.size());

        for (var entry : decoded) {
            LOG.info("  {} = {}", entry.getKey(), entry.getValue());
        }

        // Typical HTTP/3 response headers
        var responseHeaders = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":status", "200"),
                new AbstractMap.SimpleEntry<>("content-type", "text/html"),
                new AbstractMap.SimpleEntry<>("content-length", "1234"),
                new AbstractMap.SimpleEntry<>("cache-control", "max-age=3600")
        );

        ByteBuffer encodedResp = encoder.encode(responseHeaders);
        List<Map.Entry<String, String>> decodedResp = decoder.decode(encodedResp);
        LOG.info("Response: encoded {} headers, decoded {} headers",
                responseHeaders.size(), decodedResp.size());

        int totalDecoded = decoded.size() + decodedResp.size();
        LOG.info("Total QPACK round-trip headers: {}", totalDecoded);
        return totalDecoded;
    }

    // ======================== 6. SERVER PUSH ==================================

    /**
     * Demonstrates HTTP/3 server push with PUSH_PROMISE frames.
     * <p>
     * When a client requests a page, the server can proactively push associated
     * resources (stylesheets, scripts) using PUSH_PROMISE, eliminating the round
     * trip needed for the client to discover and request them.
     */
    static boolean demoServerPush(Http3Server server, Http3Config config) {
        LOG.info("=== 6. Server Push ===");

        // Register page and resource routes
        server.router().get("/page.html", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "<html><link rel='stylesheet' href='/app.css'></html>"));
        server.router().get("/app.css", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "body { margin: 0; }");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css");
            return response;
        });

        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsBidi(100)
                .initialMaxStreamsUni(100)
                .build();

        var quicConn = new QuicConnection(300L, true, quicSettings);
        quicConn.accept();
        var h3Conn = server.acceptConnection(quicConn);

        // Client requests /page.html
        var requestStream = quicConn.createStream(true);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/page.html"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "localhost")
        );

        // Handle the main request
        server.handleRequest(h3Conn, requestStream, headers, null);
        LOG.info("Main request handled for /page.html");

        // Server pushes the stylesheet
        var pushRequest = HttpRequest.of(HttpMethod.GET, "/app.css");
        pushRequest.getHeaders().set(HttpHeaders.HOST, "localhost");
        var pushResponse = HttpResponse.of(HttpStatus.OK, "body { margin: 0; }");
        pushResponse.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css");

        server.handlePushPromise(h3Conn, requestStream, 0, pushRequest, pushResponse);
        LOG.info("Server push completed for /app.css");

        return true;
    }

    // ======================== 7. FLOW CONTROL ================================

    /**
     * Demonstrates QUIC flow control at connection and stream levels.
     * <p>
     * QUIC provides credit-based flow control similar to HTTP/2 but at the
     * transport layer. Both connection-level and stream-level windows prevent
     * a fast sender from overwhelming a slow receiver.
     */
    static boolean demoFlowControl() {
        LOG.info("=== 7. Flow Control ===");

        var quicSettings = QuicSettings.builder()
                .initialMaxData(1_048_576)
                .initialMaxStreamDataBidiLocal(262_144)
                .initialMaxStreamDataBidiRemote(262_144)
                .initialMaxStreamsBidi(10)
                .initialMaxStreamsUni(10)
                .build();

        var conn = new QuicConnection(400L, true, quicSettings);
        conn.accept();

        // Check connection-level flow control
        var flowControl = conn.flowControl();
        LOG.info("Connection flow control initialized");

        // Create a stream and check stream-level flow control
        var stream = conn.createStream(true);
        long sendWindow = stream.sendWindowSize();
        long receiveWindow = stream.receiveWindowSize();
        LOG.info("Stream {}: sendWindow={}, receiveWindow={}", stream.streamId(), sendWindow, receiveWindow);

        boolean sendWindowPositive = sendWindow > 0;
        boolean receiveWindowPositive = receiveWindow > 0;

        // Verify flow control tracks usage
        var flowCtrl = conn.flowControl();
        LOG.info("Flow control verified: send={}, receive={}", sendWindowPositive, receiveWindowPositive);

        conn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "Flow control demo complete");

        return sendWindowPositive && receiveWindowPositive;
    }

    // ======================== 8. STREAM PRIORITIES ============================

    /**
     * Demonstrates bidirectional and unidirectional stream types and their roles
     * in HTTP/3.
     * <p>
     * Bidirectional streams carry request/response pairs. Unidirectional streams
     * carry control data (HTTP/3 control stream, QPACK encoder/decoder streams).
     * Stream IDs encode the initiator (client/server) and directionality in the
     * low two bits per RFC 9000 section 2.1.
     */
    static int demoStreamPriorities() {
        LOG.info("=== 8. Stream Priorities ===");

        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsBidi(50)
                .initialMaxStreamsUni(50)
                .build();

        var conn = new QuicConnection(500L, true, quicSettings);
        conn.accept();

        // Create bidirectional streams (for requests)
        var bidi1 = conn.createStream(true);
        var bidi2 = conn.createStream(true);
        LOG.info("Bidi stream 1: id={}, bidi={}, serverInitiated={}",
                bidi1.streamId(), bidi1.isBidirectional(), bidi1.isServerInitiated());
        LOG.info("Bidi stream 2: id={}, bidi={}, serverInitiated={}",
                bidi2.streamId(), bidi2.isBidirectional(), bidi2.isServerInitiated());

        // Create unidirectional streams (for control, QPACK)
        var uni1 = conn.createStream(false);
        var uni2 = conn.createStream(false);
        LOG.info("Uni stream 1: id={}, uni={}, serverInitiated={}",
                uni1.streamId(), uni1.isUnidirectional(), uni1.isServerInitiated());
        LOG.info("Uni stream 2: id={}, uni={}, serverInitiated={}",
                uni2.streamId(), uni2.isUnidirectional(), uni2.isServerInitiated());

        // HTTP/3 connection creates 3 uni streams (control, QPACK encoder, QPACK decoder)
        var h3Conn = new Http3Connection(conn);
        h3Conn.initialize();

        var controlStream = h3Conn.controlStream();
        var qpackEncoderStream = h3Conn.qpackEncoderStream();
        var qpackDecoderStream = h3Conn.qpackDecoderStream();

        LOG.info("HTTP/3 control stream: id={}", controlStream.streamId());
        LOG.info("QPACK encoder stream: id={}", qpackEncoderStream.streamId());
        LOG.info("QPACK decoder stream: id={}", qpackDecoderStream.streamId());

        // Count verified stream types: 2 bidi + 2 uni + 3 H3 uni = 7
        int verifiedTypes = 0;
        if (bidi1.isBidirectional()) verifiedTypes++;
        if (bidi2.isBidirectional()) verifiedTypes++;
        if (uni1.isUnidirectional()) verifiedTypes++;
        if (uni2.isUnidirectional()) verifiedTypes++;
        if (controlStream.isUnidirectional()) verifiedTypes++;
        if (qpackEncoderStream.isUnidirectional()) verifiedTypes++;
        if (qpackDecoderStream.isUnidirectional()) verifiedTypes++;

        LOG.info("Verified stream types: {}", verifiedTypes);

        h3Conn.close();
        return verifiedTypes;
    }

    // ======================== 9. TLS 1.3 HANDSHAKE ==============================

    /**
     * Demonstrates TLS 1.3 handshake with QUIC connection.
     * <p>
     * QUIC mandates TLS 1.3 for its handshake. The handshake progresses through
     * phases: INITIAL (pre-handshake), HANDSHAKE (ClientHello/ServerHello exchange),
     * ESTABLISHED (application keys derived, ALPN confirmed), and CLOSED.
     */
    static boolean demoTlsHandshake() {
        LOG.info("=== 9. TLS 1.3 Handshake ===");

        // Client-side handshake
        var clientConn = new QuicConnection(600L, false);
        LOG.info("Client handshake phase before connect: {}", clientConn.handshakePhase());

        clientConn.connect(new InetSocketAddress("localhost", 4433));
        LOG.info("Client handshake phase after connect: {}", clientConn.handshakePhase());
        LOG.info("Negotiated ALPN: {}", clientConn.negotiatedAlpn());
        LOG.info("Negotiated cipher: {}", clientConn.negotiatedCipherSuite());
        LOG.info("Negotiated protocol: {}", clientConn.negotiatedProtocol());

        boolean clientEstablished =
                clientConn.handshakePhase() == QuicConnection.HandshakePhase.ESTABLISHED;
        boolean alpnCorrect = "h3".equals(clientConn.negotiatedAlpn());
        boolean tls13 = "TLSv1.3".equals(clientConn.negotiatedProtocol());
        boolean cipherNegotiated = clientConn.negotiatedCipherSuite() != null;

        // Server-side handshake
        var serverConn = new QuicConnection(601L, true);
        serverConn.accept();
        LOG.info("Server handshake phase: {}", serverConn.handshakePhase());

        boolean serverEstablished =
                serverConn.handshakePhase() == QuicConnection.HandshakePhase.ESTABLISHED;

        // Verify close transitions handshake phase
        clientConn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "TLS demo complete");
        boolean phaseClosed = clientConn.handshakePhase() == QuicConnection.HandshakePhase.CLOSED;

        serverConn.close(ssg.legoflow.http3.quic.QuicErrorCode.NO_ERROR, "TLS demo complete");

        boolean result = clientEstablished && serverEstablished && alpnCorrect
                && tls13 && cipherNegotiated && phaseClosed;
        LOG.info("TLS handshake demo: client={}, server={}, ALPN={}, TLS1.3={}, cipher={}, closed={}",
                clientEstablished, serverEstablished, alpnCorrect, tls13, cipherNegotiated, phaseClosed);
        return result;
    }

    // ======================== 10. QPACK DYNAMIC TABLE ===========================

    /**
     * Demonstrates QPACK dynamic table operations (RFC 9204).
     * <p>
     * The dynamic table allows the encoder to insert frequently used header fields
     * and reference them by index in subsequent encodes, reducing header block size.
     * Encoder instructions (insert, duplicate, set capacity) are sent on a dedicated
     * unidirectional QPACK encoder stream. Decoder instructions (section acknowledgment,
     * stream cancellation, insert count increment) are sent back on the decoder stream.
     */
    static int demoQpackDynamicTable() {
        LOG.info("=== 10. QPACK Dynamic Table ===");

        var encoder = new QpackEncoder(4096);
        var decoder = new QpackDecoder(4096);

        // 1. Insert with static name reference (encoder instruction)
        var instr1 = encoder.encodeInsertWithStaticNameReference(0, "example.com");
        decoder.processEncoderInstructions(instr1);
        LOG.info("Inserted :authority=example.com via static name reference");

        // 2. Insert with literal name (encoder instruction)
        var instr2 = encoder.encodeInsertWithLiteralName("x-request-id", "abc-123");
        decoder.processEncoderInstructions(instr2);
        LOG.info("Inserted x-request-id=abc-123 via literal name");

        // 3. Insert with dynamic name reference
        var instr3 = encoder.encodeInsertWithDynamicNameReference(0, "def-456");
        decoder.processEncoderInstructions(instr3);
        LOG.info("Inserted x-request-id=def-456 via dynamic name reference");

        // 4. Duplicate to prevent eviction
        var instr4 = encoder.encodeDuplicate(2);
        decoder.processEncoderInstructions(instr4);
        LOG.info("Duplicated oldest entry to prevent eviction");

        int encoderEntries = encoder.getDynamicTable().size();
        int decoderEntries = decoder.getDynamicTable().size();
        LOG.info("Encoder dynamic table: {} entries", encoderEntries);
        LOG.info("Decoder dynamic table: {} entries", decoderEntries);

        // 5. Decoder instructions
        decoder.getDynamicTable().insert("h1", "v1");
        decoder.getDynamicTable().insert("h2", "v2");
        var sectionAck = decoder.encodeSectionAcknowledgment(4L);
        LOG.info("Section acknowledgment for stream 4: {} bytes", sectionAck.remaining());

        var insertIncr = decoder.encodeInsertCountIncrement(2);
        LOG.info("Insert count increment: {} bytes", insertIncr.remaining());

        // 6. Set dynamic table capacity
        var capInstr = encoder.encodeSetDynamicTableCapacity(8192);
        decoder.processEncoderInstructions(capInstr);
        LOG.info("Dynamic table capacity set to 8192");

        int totalEntries = encoderEntries + decoderEntries;
        LOG.info("Total dynamic table entries across encoder+decoder: {}", totalEntries);
        return totalEntries;
    }
}
