package ssg.legoflow.ssh.cipher;

/**
 * Interface for SSH encryption and decryption ciphers.
 *
 * <p>Implementations wrap standard JCE cipher algorithms with SSH-specific
 * block and key size requirements. The cipher handles ONLY encrypt/decrypt
 * of packet data — no MAC or packet format awareness. Packet format
 * (pktLen placement, padding) is handled by the codec layer.
 *
 * <p>Two modes of operation are supported:
 * <ul>
 *   <li><b>Full packet mode</b> (default): {@code encrypt}/{@code decrypt} receive
 *       the full packet ([pktLen][padLen][payload][padding]). Used by AES-CTR and
 *       AES-GCM ciphers.</li>
 *   <li><b>OpenSSH AEAD mode</b> (ChaCha20-Poly1305): {@code encryptPayload}/{@code
 *       decryptPayload} receive only the payload portion ([padLen][payload][padding]).
 *       The codec extracts the packet length for wire format handling.</li>
 * </ul>
 *
 * @since 0.1.0
 */
public interface SshCipher {

    String name();
    int blockSize();
    int keySize();
    int ivSize();
    int nonceLen();
    boolean isAead();
    int authTagLength();

    /**
     * Returns whether this cipher uses OpenSSH AEAD payload-only mode.
     * When true, the codec handles pktLen separately (leaves it unencrypted)
     * and passes only the payload to encryptPayload/decryptPayload.
     *
     * <p>Default: false (full-packet mode used by AES-CTR, AES-GCM).
     */
    default boolean isPayloadOnly() { return false; }

    void init(byte[] key, byte[] iv, boolean encrypt);

    /**
     * Sets the sequence number for this cipher. Used by AEAD ciphers
     * to construct the nonce (e.g., "GCM " ^ seq for AES-GCM).
     */
    default void setSequenceNumber(long sequenceNumber) {}

    /**
     * Generates the nonce for a given sequence number. Used by the codec
     * for AEAD ciphers where the nonce is derived from a base nonce XORed with seq.
     */
    default byte[] makeNonce(long sequenceNumber) {
        return new byte[nonceLen()];
    }

    /**
     * Sets additional authenticated data (AAD) for AEAD ciphers.
     * Called by the codec before encrypt/decrypt to pass the 4-byte
     * packet length as AAD (per OpenSSH convention for AES-GCM).
     */
    default void setAad(byte[] aad) {}

    /**
     * Clears previously set AAD for AEAD ciphers.
     */
    default void clearAad() { setAad(new byte[4]); }

    /**
     * Encrypts full packet data ([pktLen][padLen][payload][padding]) with AAD.
     * For AEAD ciphers, returns [ciphertext][authTag].
     * The AAD (4-byte packet length) is bound to the ciphertext for integrity.
     */
    default byte[] encryptWithAad(byte[] packet, byte[] aad) {
        throw new UnsupportedOperationException("Use encrypt for non-AEAD ciphers");
    }

    /**
     * Decrypts full packet data ([ciphertext][authTag]) with AAD.
     * For AEAD ciphers, returns [pktLen][padLen][payload][padding].
     * The AAD (4-byte plaintext packet length) must match what the sender used.
     */
    default byte[] decryptWithAad(byte[] ctWithTag, byte[] aad) {
        throw new UnsupportedOperationException("Use decrypt for non-AEAD ciphers");
    }

    /**
     * Encrypts full packet data ([pktLen][padLen][payload][padding]).
     * For AEAD ciphers, returns [ciphertext][authTag].
     * Default: delegates to encryptWithAad.
     */
    default byte[] encrypt(byte[] data) {
        throw new UnsupportedOperationException("Use encryptWithAad for AEAD ciphers");
    }

    /**
     * Decrypts full packet data ([ciphertext][authTag]).
     * For AEAD ciphers, returns [pktLen][padLen][payload][padding].
     * Default: delegates to decryptWithAad.
     */
    default byte[] decrypt(byte[] data) {
        throw new UnsupportedOperationException("Use decryptWithAad for AEAD ciphers");
    }

    /**
     * Encrypts ONLY the payload portion ([padLen][payload][padding]).
     * Used by OpenSSH AEAD ciphers (ChaCha20-Poly1305) where the packet
     * length is handled separately at the codec layer.
     *
     * <p>Return format: [encryptedPayload][authTag]
     *
     * @param payloadWithPadding the payload portion (padLen byte + payload + padding)
     * @return encrypted payload followed by auth tag
     */
    default byte[] encryptPayload(byte[] payloadWithPadding) {
        throw new UnsupportedOperationException(
            name() + " does not support payload-level encryption");
    }

    /**
     * Decrypts ONLY the payload portion.
     * Used by OpenSSH AEAD ciphers (ChaCha20-Poly1305) where the packet
     * length is handled separately at the codec layer.
     *
     * <p>Input format: [encryptedPayload][authTag]
     *
     * @param encryptedWithTag the encrypted payload and tag
     * @return decrypted payload with padding ([padLen][payload][padding])
     */
    default byte[] decryptPayload(byte[] encryptedWithTag) {
        throw new UnsupportedOperationException(
            name() + " does not support payload-level decryption");
    }
}
