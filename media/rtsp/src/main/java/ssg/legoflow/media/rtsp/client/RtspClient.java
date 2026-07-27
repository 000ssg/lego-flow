package ssg.legoflow.media.rtsp.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.rtsp.protocol.*;
import ssg.legoflow.media.rtsp.server.RtspServer;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RTSP 2.0 client for controlling media streams.
 *
 * <p>Provides high-level methods for the RTSP request/response exchange:
 * OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN, GET_PARAMETER.
 *
 * <p>The client manages CSeq numbering and session tracking automatically.
 *
 * <p>Usage:
 * <pre>{@code
 * var client = new RtspClient("rtsp://server/media");
 * var options = client.options();
 * var sdp = client.describe();
 * var setup = client.setup("RTP/AVP;unicast;client_port=8000-8001");
 * var play = client.play();
 * client.teardown();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class RtspClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(RtspClient.class);

    private final URI serverUri;
    private final AtomicInteger cseqCounter;
    private final RtspServer serverRef;
    private volatile RtspClientSession session;
    private volatile boolean closed;

    /**
     * Creates an RTSP client connecting to the given URI.
     *
     * <p>For testing, this client can work with a local {@link RtspServer}
     * instance directly without TCP networking.
     *
     * @param serverUri the RTSP server URI
     * @param server    the server to communicate with (for testing)
     */
    public RtspClient(URI serverUri, RtspServer server) {
        this.serverUri = Objects.requireNonNull(serverUri, "serverUri");
        this.serverRef = Objects.requireNonNull(server, "server");
        this.cseqCounter = new AtomicInteger(0);
        this.closed = false;
    }

    /**
     * Creates an RTSP client for the given URI string with a server reference.
     *
     * @param uri    the RTSP server URI string
     * @param server the server reference
     */
    public RtspClient(String uri, RtspServer server) {
        this(URI.create(uri), server);
    }

    /**
     * Sends an OPTIONS request to query server capabilities.
     *
     * @return the server response
     */
    public RtspResponse options() {
        var request = RtspRequest.builder(RtspMethod.OPTIONS, serverUri)
                .cseq(nextCseq())
                .userAgent("LegoFlow-RTSP-Client/2.0")
                .build();
        return send(request);
    }

    /**
     * Sends a DESCRIBE request to get the media description (SDP).
     *
     * @return the server response containing SDP
     */
    public RtspResponse describe() {
        var request = RtspRequest.builder(RtspMethod.DESCRIBE, serverUri)
                .cseq(nextCseq())
                .accept("application/sdp")
                .userAgent("LegoFlow-RTSP-Client/2.0")
                .build();
        return send(request);
    }

    /**
     * Sends a SETUP request to establish transport.
     *
     * @param transport the Transport header value
     * @return the setup result with session and transport info
     */
    public SetupResult setup(String transport) {
        var builder = RtspRequest.builder(RtspMethod.SETUP, serverUri)
                .cseq(nextCseq())
                .transport(transport)
                .userAgent("LegoFlow-RTSP-Client/2.0");
        if (session != null) {
            builder.session(session.sessionId());
        }
        var response = send(builder.build());

        if (!response.isSuccess()) {
            throw new RuntimeException("SETUP failed: " + response.status());
        }

        String sessionId = response.headers().sessionId()
                .orElseThrow(() -> new RuntimeException("No Session in SETUP response"));
        int timeout = response.headers().sessionTimeout().orElse(60);
        TransportHeader serverTransport = TransportHeader.parse(
                response.headers().first(RtspHeaders.TRANSPORT).orElse(transport));

        var result = new SetupResult(sessionId, timeout, serverTransport);
        session = new RtspClientSession(result);
        return result;
    }

    /**
     * Sends a PLAY request to start playback.
     *
     * @return the server response
     */
    public RtspResponse play() {
        return play("npt=0-");
    }

    /**
     * Sends a PLAY request with a specific range.
     *
     * @param range the Range header value
     * @return the server response
     */
    public RtspResponse play(String range) {
        requireSession();
        var request = RtspRequest.builder(RtspMethod.PLAY, serverUri)
                .cseq(nextCseq())
                .session(session.sessionId())
                .range(range)
                .build();
        return send(request);
    }

    /**
     * Sends a PAUSE request to pause playback.
     *
     * @return the server response
     */
    public RtspResponse pause() {
        requireSession();
        var request = RtspRequest.builder(RtspMethod.PAUSE, serverUri)
                .cseq(nextCseq())
                .session(session.sessionId())
                .build();
        return send(request);
    }

    /**
     * Sends a TEARDOWN request to end the session.
     *
     * @return the server response
     */
    public RtspResponse teardown() {
        requireSession();
        var request = RtspRequest.builder(RtspMethod.TEARDOWN, serverUri)
                .cseq(nextCseq())
                .session(session.sessionId())
                .build();
        var response = send(request);
        if (session != null) {
            session.terminate();
        }
        return response;
    }

    /**
     * Sends a GET_PARAMETER request (used as keep-alive).
     *
     * @return the server response
     */
    public RtspResponse getParameter() {
        requireSession();
        var request = RtspRequest.builder(RtspMethod.GET_PARAMETER, serverUri)
                .cseq(nextCseq())
                .session(session.sessionId())
                .build();
        var response = send(request);
        if (session != null) {
            session.markKeepAlive();
        }
        return response;
    }

    /**
     * Returns the current session, or empty if not established.
     *
     * @return the session
     */
    public Optional<RtspClientSession> session() {
        return Optional.ofNullable(session);
    }

    /**
     * Returns the current CSeq counter value.
     *
     * @return the current CSeq
     */
    public int currentCseq() {
        return cseqCounter.get();
    }

    private RtspResponse send(RtspRequest request) {
        LOG.debug("Sending: {}", request);
        var response = serverRef.handleRequest(request);
        LOG.debug("Received: {}", response);
        return response;
    }

    private int nextCseq() {
        return cseqCounter.incrementAndGet();
    }

    private void requireSession() {
        if (session == null) {
            throw new IllegalStateException("No active session. Call setup() first.");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            if (session != null && session.isActive()) {
                try {
                    teardown();
                } catch (Exception e) {
                    LOG.debug("Error during teardown on close", e);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "RtspClient[uri=" + serverUri + ", session=" + session + "]";
    }
}
