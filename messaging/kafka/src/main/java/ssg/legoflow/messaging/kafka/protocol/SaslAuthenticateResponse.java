package ssg.legoflow.messaging.kafka.protocol;

/**
 * SaslAuthenticate response (API key 36).
 *
 * <p>Per-version layout (Kafka 3.6.1, {@code SaslAuthenticateResponse.json}):
 * v0 — {@code errorCode, errorMessage, authBytes}; v1 — adds
 * {@code sessionLifetimeMs} (int64); v2 — flexible encoding.
 *
 * @param errorCode         the error code
 * @param errorMessage      the error message (always present, may be empty)
 * @param authBytes         the authentication response bytes from the server
 * @param sessionLifetimeMs the session lifetime in milliseconds (v1+; 0 when absent or unlimited)
 * @since 0.1.0
 */
public record SaslAuthenticateResponse(short errorCode, String errorMessage, byte[] authBytes, long sessionLifetimeMs) {

    /**
     * Convenience constructor for v0 with no session lifetime.
     *
     * @param errorCode  the error code
     * @param authBytes  the authentication response bytes from the server
     * @param lifetimeMs the session lifetime in milliseconds
     */
    public SaslAuthenticateResponse(short errorCode, byte[] authBytes, long lifetimeMs) {
        this(errorCode, "", authBytes, lifetimeMs);
    }
}
