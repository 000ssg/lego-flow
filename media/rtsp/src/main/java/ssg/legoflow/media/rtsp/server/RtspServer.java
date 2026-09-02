package ssg.legoflow.media.rtsp.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.rtsp.protocol.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
/**
 * RTSP 2.0 server implementation supporting TCP connections.
 *
 * <p>The server listens for TCP connections and handles RTSP requests
 * for media streaming control. Each client connection is processed
 * on a virtual thread.
 *
 * <p>Usage:
 * <pre>{@code
 * var server = new RtspServer(8554);
 * server.registerMedia(myMediaSource);
 * server.start();
 * }</pre>
 *
 * @since 0.1.0
 */
public final class RtspServer implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RtspServer.class);

    /** Default RTSP port. */
    public static final int DEFAULT_PORT = 554;

    private final int port;
    private final Map<String, MediaSource> mediaSources;
    private final Map<String, RtspSession> sessions;
    private final String serverName;
    private final AtomicBoolean running;
    private volatile ServerSocket serverSocket;
    private java.util.concurrent.ExecutorService executor;

    /**
     * Creates an RTSP server on the specified port.
     *
     * @param port the port to listen on
     */
    public RtspServer(int port) {
        this.port = port;
        this.mediaSources = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.serverName = "LegoFlow-RTSP/2.0";
        this.running = new AtomicBoolean(false);
    }

    /**
     * Creates an RTSP server on the default port (554).
     */
    public RtspServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Registers a media source.
     *
     * @param source the media source to register
     */
    public void registerMedia(MediaSource source) {
        Objects.requireNonNull(source, "source");
        mediaSources.put(source.path(), source);
        LOG.info("Registered media source: {}", source.path());
    }

    /**
     * Unregisters a media source.
     *
     * @param path the media path to unregister
     */
    public void unregisterMedia(String path) {
        mediaSources.remove(path);
    }

    /**
     * Handles an RTSP request and returns a response.
     *
     * @param request the RTSP request
     * @return the RTSP response
     */
    public RtspResponse handleRequest(RtspRequest request) {
        int cseq = request.headers().cseq();
        String uri = request.uri().getPath();

        return switch (request.method()) {
            case OPTIONS -> handleOptions(request, cseq);
            case DESCRIBE -> handleDescribe(request, cseq, uri);
            case SETUP -> handleSetup(request, cseq, uri);
            case PLAY -> handlePlay(request, cseq);
            case PAUSE -> handlePause(request, cseq);
            case TEARDOWN -> handleTeardown(request, cseq);
            case GET_PARAMETER -> handleGetParameter(request, cseq);
            case SET_PARAMETER -> handleSetParameter(request, cseq);
            case ANNOUNCE -> handleAnnounce(request, cseq);
            case RECORD -> handleRecord(request, cseq);
        };
    }

    private RtspResponse handleOptions(RtspRequest request, int cseq) {
        return RtspResponse.builder(RtspStatus.OK)
                .cseq(cseq)
                .publicMethods("OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN, GET_PARAMETER, SET_PARAMETER")
                .server(serverName)
                .build();
    }

    private RtspResponse handleDescribe(RtspRequest request, int cseq, String uri) {
        var source = findMediaSource(uri);
        if (source.isEmpty()) {
            return RtspResponse.builder(RtspStatus.NOT_FOUND).cseq(cseq).build();
        }

        // Accept header should be application/sdp
        String accept = request.headers().first(RtspHeaders.ACCEPT).orElse("application/sdp");
        if (!accept.contains("application/sdp")) {
            return RtspResponse.builder(RtspStatus.NOT_ACCEPTABLE).cseq(cseq).build();
        }

        var sdp = source.get().describe();
        String sdpBody = sdp.toString(); // Simplified; full SDP writer would be in media/common

        return RtspResponse.builder(RtspStatus.OK)
                .cseq(cseq)
                .server(serverName)
                .body(sdpBody, "application/sdp")
                .build();
    }

    private RtspResponse handleSetup(RtspRequest request, int cseq, String uri) {
        String transportValue = request.headers().first(RtspHeaders.TRANSPORT)
                .orElse(null);
        if (transportValue == null) {
            return RtspResponse.builder(RtspStatus.BAD_REQUEST).cseq(cseq).build();
        }

        TransportHeader clientTransport = TransportHeader.parse(transportValue);

        // Get or create session
        String sessionId = request.headers().sessionId().orElse(null);
        RtspSession session;
        if (sessionId != null && sessions.containsKey(sessionId)) {
            session = sessions.get(sessionId);
        } else {
            session = RtspSession.create();
            sessions.put(session.sessionId(), session);
        }

        // Setup the stream controller
        var controller = session.controller(uri);
        controller.setup();
        session.setTransport(uri, clientTransport);
        session.touch();

        // Build server transport response
        var serverTransport = new TransportHeader(
                clientTransport.protocol(),
                clientTransport.castMode(),
                clientTransport.destination(),
                Optional.of("0.0.0.0"),
                clientTransport.clientPortRtp(),
                clientTransport.clientPortRtcp(),
                clientTransport.serverPortRtp().isPresent() ? clientTransport.serverPortRtp() : java.util.OptionalInt.of(port),
                clientTransport.serverPortRtcp().isPresent() ? clientTransport.serverPortRtcp() : java.util.OptionalInt.of(port + 1),
                clientTransport.ssrc(),
                clientTransport.interleavedRtp(),
                clientTransport.interleavedRtcp()
        );

        return RtspResponse.builder(RtspStatus.OK)
                .cseq(cseq)
                .session(session.sessionId(), session.timeout())
                .transport(serverTransport.format())
                .server(serverName)
                .build();
    }

    private RtspResponse handlePlay(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            // Parse range if present
            double startPos = 0.0;
            var rangeStr = request.headers().first(RtspHeaders.RANGE);
            if (rangeStr.isPresent()) {
                var range = RangeHeader.parse(rangeStr.get());
                startPos = range.startAsSeconds().orElse(0.0);
            }

            // Play all streams in this session
            for (var entry : session.findController(request.uri().getPath()).stream().toList()) {
                entry.play(startPos);
            }
            // If no specific path controller found, play any
            if (session.findController(request.uri().getPath()).isEmpty()) {
                // Try to play all controllers
            }

            session.touch();

            var builder = RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .session(session.sessionId())
                    .server(serverName);
            rangeStr.ifPresent(r -> builder.header(RtspHeaders.RANGE, r));
            return builder.build();
        });
    }

    private RtspResponse handlePause(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            session.findController(request.uri().getPath())
                    .ifPresent(StreamController::pause);
            session.touch();
            return RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .session(session.sessionId())
                    .server(serverName)
                    .build();
        });
    }

    private RtspResponse handleTeardown(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            session.terminate();
            sessions.remove(session.sessionId());
            return RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .server(serverName)
                    .build();
        });
    }

    private RtspResponse handleGetParameter(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            session.touch();
            return RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .session(session.sessionId())
                    .server(serverName)
                    .build();
        });
    }

    private RtspResponse handleSetParameter(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            session.touch();
            return RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .session(session.sessionId())
                    .server(serverName)
                    .build();
        });
    }

    private RtspResponse handleAnnounce(RtspRequest request, int cseq) {
        return RtspResponse.builder(RtspStatus.OK)
                .cseq(cseq)
                .server(serverName)
                .build();
    }

    private RtspResponse handleRecord(RtspRequest request, int cseq) {
        return withSession(request, cseq, (session) -> {
            session.findController(request.uri().getPath())
                    .ifPresent(StreamController::record);
            session.touch();
            return RtspResponse.builder(RtspStatus.OK)
                    .cseq(cseq)
                    .session(session.sessionId())
                    .server(serverName)
                    .build();
        });
    }

    private RtspResponse withSession(RtspRequest request, int cseq,
                                      java.util.function.Function<RtspSession, RtspResponse> handler) {
        var sessionId = request.headers().sessionId();
        if (sessionId.isEmpty()) {
            return RtspResponse.builder(RtspStatus.SESSION_NOT_FOUND).cseq(cseq).build();
        }
        var session = sessions.get(sessionId.get());
        if (session == null || session.isTerminated()) {
            return RtspResponse.builder(RtspStatus.SESSION_NOT_FOUND).cseq(cseq).build();
        }
        return handler.apply(session);
    }

    private Optional<MediaSource> findMediaSource(String uri) {
        // Try exact match first, then prefix match
        if (mediaSources.containsKey(uri)) {
            return Optional.of(mediaSources.get(uri));
        }
        return mediaSources.values().stream()
                .filter(s -> uri.startsWith(s.path()))
                .findFirst();
    }

    /**
     * Returns the number of active sessions.
     *
     * @return the session count
     */
    public int sessionCount() {
        return sessions.size();
    }

    /**
     * Gets a session by ID.
     *
     * @param sessionId the session identifier
     * @return the session, or empty
     */
    public Optional<RtspSession> session(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    /**
     * Returns the server port.
     *
     * @return the port
     */
    public int port() {
        return port;
    }

    /**
     * Returns the server name.
     *
     * @return the server name
     */
    public String serverName() {
        return serverName;
    }

    /**
     * Returns true if the server is running.
     *
     * @return true if running
     */
    
    /**
     * Starts the RTSP server and begins accepting connections.
     * Uses virtual threads for connection handling.
     *
     * @throws IOException if binding fails
     */
    public void start() throws IOException {
        if (running.get()) return;

        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new java.net.InetSocketAddress(port));
        running.set(true);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        LOG.info("RTSP server started on port {}", port);

        // Accept loop on virtual thread
        executor.submit(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                executor.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running.get()) LOG.warn("Accept error", e);
            }
        }
    }

    private void handleClient(Socket socket) {
        try {
            // RTSP connections are handled through the HTTP framework layer;
            // this method is a placeholder for future transport integration.
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Client handler error", e);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void close() {
        running.set(false);
        if (executor != null) {
            executor.shutdownNow();
        }
        sessions.values().forEach(RtspSession::terminate);
        sessions.clear();
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                LOG.warn("Error closing server socket", e);
            }
        }
    }

    @Override
    public String toString() {
        return "RtspServer[port=" + port + ", sessions=" + sessions.size() + "]";
    }
}
