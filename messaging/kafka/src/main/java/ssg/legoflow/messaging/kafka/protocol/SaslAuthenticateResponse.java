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

    /**
     * Entry point for the {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Hybrid builder for {@link SaslAuthenticateResponse}: the convenience constructor remains;
     * this named-field builder is the preferred entry point for new call sites. Each field
     * defaults to the spec value it takes when its version feature is absent
     * ({@code errorMessage = ""} is always present, {@code sessionLifetimeMs = 0} is the v1+
     * absent value). {@code build()} delegates to the canonical constructor.
     */
    public static final class Builder {
        private short errorCode = 0;
        private String errorMessage = "";
        private byte[] authBytes = new byte[0];
        private long sessionLifetimeMs = 0L; // v1+; 0 = absent / unlimited

        public Builder errorCode(short v) { this.errorCode = v; return this; }
        public Builder errorMessage(String v) { this.errorMessage = v; return this; }
        public Builder authBytes(byte[] v) { this.authBytes = v; return this; }
        public Builder sessionLifetimeMs(long v) { this.sessionLifetimeMs = v; return this; }

        public SaslAuthenticateResponse build() {
            return new SaslAuthenticateResponse(errorCode, errorMessage, authBytes, sessionLifetimeMs);
        }
    }
}
