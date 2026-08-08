package ssg.legoflow.media.rtsp.server;

import ssg.legoflow.media.rtsp.protocol.TransportHeader;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side RTSP session representing a client connection.
 *
 * <p>Each session has a unique identifier, a timeout value, and manages
 * one or more stream controllers for the media streams within the session.
 *
 * @since 0.1.0
 */
public final class RtspSession {

    /** Default session timeout in seconds. */
    public static final int DEFAULT_TIMEOUT = 60;

    private final String sessionId;
    private final int timeout;
    private final Map<String, StreamController> controllers;
    private final Map<String, TransportHeader> transports;
    private volatile Instant lastActivity;
    private volatile boolean terminated;

    /**
     * Creates a new RTSP session.
     *
     * @param sessionId the session identifier
     * @param timeout   the session timeout in seconds
     */
    public RtspSession(String sessionId, int timeout) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.timeout = timeout;
        this.controllers = new ConcurrentHashMap<>();
        this.transports = new ConcurrentHashMap<>();
        this.lastActivity = Instant.now();
        this.terminated = false;
    }

    /**
     * Creates a new session with a generated ID and default timeout.
     *
     * @return a new session
     */
    public static RtspSession create() {
        return new RtspSession(UUID.randomUUID().toString(), DEFAULT_TIMEOUT);
    }

    /**
     * Creates a new session with a generated ID and specified timeout.
     *
     * @param timeout the session timeout in seconds
     * @return a new session
     */
    public static RtspSession create(int timeout) {
        return new RtspSession(UUID.randomUUID().toString(), timeout);
    }

    /** Returns the session identifier. */
    public String sessionId() { return sessionId; }

    /** Returns the session timeout in seconds. */
    public int timeout() { return timeout; }

    /** Returns true if the session has been terminated. */
    public boolean isTerminated() { return terminated; }

    /**
     * Returns true if the session has expired based on the timeout.
     *
     * @return true if expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(lastActivity.plusSeconds(timeout));
    }

    /**
     * Touches the session to update the last activity time.
     */
    public void touch() {
        this.lastActivity = Instant.now();
    }

    /**
     * Gets or creates a stream controller for a media path.
     *
     * @param mediaPath the media path (from the SETUP URI)
     * @return the stream controller
     */
    public StreamController controller(String mediaPath) {
        return controllers.computeIfAbsent(mediaPath, k -> new StreamController());
    }

    /**
     * Gets the stream controller for a media path, if it exists.
     *
     * @param mediaPath the media path
     * @return the controller, or empty
     */
    public Optional<StreamController> findController(String mediaPath) {
        return Optional.ofNullable(controllers.get(mediaPath));
    }

    /**
     * Sets the transport for a media path.
     *
     * @param mediaPath the media path
     * @param transport the transport header
     */
    public void setTransport(String mediaPath, TransportHeader transport) {
        transports.put(mediaPath, transport);
    }

    /**
     * Gets the transport for a media path.
     *
     * @param mediaPath the media path
     * @return the transport header, or empty
     */
    public Optional<TransportHeader> transport(String mediaPath) {
        return Optional.ofNullable(transports.get(mediaPath));
    }

    /**
     * Returns the number of stream controllers in this session.
     *
     * @return the stream count
     */
    public int streamCount() {
        return controllers.size();
    }

    /**
     * Terminates this session and all stream controllers.
     */
    public void terminate() {
        terminated = true;
        controllers.values().forEach(StreamController::teardown);
    }

    @Override
    public String toString() {
        return "RtspSession[id=" + sessionId + ", streams=" + controllers.size()
                + ", terminated=" + terminated + "]";
    }
}
