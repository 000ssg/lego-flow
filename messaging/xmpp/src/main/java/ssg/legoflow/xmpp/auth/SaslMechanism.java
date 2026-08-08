package ssg.legoflow.xmpp.auth;

/**
 * SASL authentication mechanisms supported in XMPP (RFC 6120).
 *
 * @since 0.1.0
 */
public enum SaslMechanism {

    /** Simple username/password mechanism (base64 encoded). */
    PLAIN,

    /** SCRAM-SHA-1 challenge-response mechanism. */
    SCRAM_SHA_1,

    /** SCRAM-SHA-256 challenge-response mechanism. */
    SCRAM_SHA_256,

    /** External authentication (e.g., TLS client certificates). */
    EXTERNAL,

    /** Anonymous authentication. */
    ANONYMOUS;

    /**
     * Returns the SASL mechanism name as used in XMPP negotiation.
     *
     * @return the mechanism name string
     */
    public String mechanismName() {
        return switch (this) {
            case PLAIN -> "PLAIN";
            case SCRAM_SHA_1 -> "SCRAM-SHA-1";
            case SCRAM_SHA_256 -> "SCRAM-SHA-256";
            case EXTERNAL -> "EXTERNAL";
            case ANONYMOUS -> "ANONYMOUS";
        };
    }
}
