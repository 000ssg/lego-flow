package ssg.legoflow.ssh.kex;


/**
 * Interface for SSH key exchange algorithms.
 *
 * <p>Implementations provide the cryptographic operations needed to establish
 * a shared secret between client and server per RFC 4253 section 8.
 *
 * @since 0.1.0
 */
public interface KexAlgorithm {

    /**
     * Returns the algorithm name as used in SSH negotiation.
     *
     * @return the algorithm name (e.g., "diffie-hellman-group14-sha256")
     */
    String name();

    /**
     * Returns the hash algorithm used for this key exchange.
     *
     * @return the hash algorithm name (e.g., "SHA-256", "SHA-512")
     */
    String hashAlgorithm();

    /**
     * Initializes the key exchange and generates the local key pair or public value.
     */
    void init();

    /**
     * Returns the local public value (e or f for DH, Q_C or Q_S for ECDH).
     *
     * @return the local public value as bytes
     */
    byte[] localPublicValue();

    /**
     * Computes the shared secret from the remote peer's public value.
     *
     * @param remotePublicValue the remote peer's public value
     * @return the shared secret as bytes (in SSH mpint format)
     */
    byte[] computeSharedSecret(byte[] remotePublicValue);

    /**
     * Computes the exchange hash H per RFC 4253 section 8.
     *
     * @param clientVersion the client's version string (without CR LF)
     * @param serverVersion the server's version string (without CR LF)
     * @param clientKexInit the client's KEXINIT payload (raw bytes)
     * @param serverKexInit the server's KEXINIT payload (raw bytes)
     * @param hostKey       the server's public host key blob
     * @param e             the client's public value
     * @param f             the server's public value
     * @param sharedSecret  the shared secret K
     * @return the exchange hash H
     */
    byte[] computeExchangeHash(
            String clientVersion, String serverVersion,
            byte[] clientKexInit, byte[] serverKexInit,
            byte[] hostKey,
            byte[] e, byte[] f,
            byte[] sharedSecret);
}
