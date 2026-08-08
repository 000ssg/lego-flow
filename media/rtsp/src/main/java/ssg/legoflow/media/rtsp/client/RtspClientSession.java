package ssg.legoflow.media.rtsp.client;

import ssg.legoflow.media.rtsp.protocol.TransportHeader;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Client-side RTSP session tracking.
 *
 * <p>Maintains the session ID, timeout, and transport parameters
 * assigned by the server.
 *
 * @since 0.1.0
 */
public final class RtspClientSession {

    private final String sessionId;
    private final int timeout;
    private volatile TransportHeader transport;
    private volatile Instant lastKeepAlive;
    private volatile boolean active;

    /**
     * Creates a client session from a setup result.
     *
     * @param result the setup result from the server
     */
    public RtspClientSession(SetupResult result) {
        this.sessionId = result.sessionId();
        this.timeout = result.timeout();
        this.transport = result.transport();
        this.lastKeepAlive = Instant.now();
        this.active = true;
    }

    /**
     * Creates a client session.
     *
     * @param sessionId the session identifier
     * @param timeout   the session timeout in seconds
     */
    public RtspClientSession(String sessionId, int timeout) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.timeout = timeout;
        this.lastKeepAlive = Instant.now();
        this.active = true;
    }

    /** Returns the session identifier. */
    public String sessionId() { return sessionId; }

    /** Returns the session timeout in seconds. */
    public int timeout() { return timeout; }

    /** Returns the negotiated transport, or null if not yet set. */
    public TransportHeader transport() { return transport; }

    /** Returns true if the session is active. */
    public boolean isActive() { return active; }

    /**
     * Returns true if a keep-alive should be sent.
     *
     * @return true if keep-alive is needed
     */
    public boolean needsKeepAlive() {
        return active && Instant.now().isAfter(
                lastKeepAlive.plusSeconds(timeout / 2));
    }

    /**
     * Records that a keep-alive was sent.
     */
    public void markKeepAlive() {
        this.lastKeepAlive = Instant.now();
    }

    /**
     * Sets the transport parameters.
     *
     * @param transport the transport header
     */
    public void setTransport(TransportHeader transport) {
        this.transport = transport;
    }

    /**
     * Terminates this session.
     */
    public void terminate() {
        this.active = false;
    }

    @Override
    public String toString() {
        return "RtspClientSession[id=" + sessionId + ", active=" + active + "]";
    }
}
