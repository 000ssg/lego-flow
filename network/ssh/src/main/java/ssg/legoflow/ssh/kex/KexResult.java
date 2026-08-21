package ssg.legoflow.ssh.kex;

import java.util.Objects;

/**
 * Result of an SSH key exchange containing the shared secret, exchange hash, and session ID.
 *
 * @param sharedSecret the computed shared secret K (raw big-endian bytes)
 * @param exchangeHash the exchange hash H
 * @param sessionId    the session identifier (exchange hash from the first key exchange)
 * @since 0.1.0
 */
public record KexResult(
        byte[] sharedSecret,
        byte[] exchangeHash,
        byte[] sessionId
) {
    /**
     * Creates a new key exchange result.
     *
     * @param sharedSecret the shared secret K (raw bytes)
     * @param exchangeHash the exchange hash H
     * @param sessionId    the session identifier
     */
    public KexResult {
        Objects.requireNonNull(sharedSecret, "sharedSecret");
        Objects.requireNonNull(exchangeHash, "exchangeHash");
        Objects.requireNonNull(sessionId, "sessionId");
    }
}
