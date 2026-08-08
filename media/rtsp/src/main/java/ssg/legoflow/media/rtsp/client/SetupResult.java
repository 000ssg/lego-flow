package ssg.legoflow.media.rtsp.client;

import ssg.legoflow.media.rtsp.protocol.TransportHeader;

import java.util.Objects;

/**
 * Result of an RTSP SETUP operation containing transport parameters
 * negotiated with the server.
 *
 * @param sessionId the server-assigned session identifier
 * @param timeout   the session timeout in seconds
 * @param transport the negotiated transport parameters
 * @since 0.1.0
 */
public record SetupResult(String sessionId, int timeout, TransportHeader transport) {

    /**
     * Creates a setup result with validation.
     */
    public SetupResult {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(transport, "transport");
        if (timeout <= 0) {
            throw new IllegalArgumentException("Timeout must be positive: " + timeout);
        }
    }

    @Override
    public String toString() {
        return "SetupResult[session=" + sessionId + ", timeout=" + timeout + "s]";
    }
}
