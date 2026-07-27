package ssg.legoflow.network.snmp.security;

/**
 * Privacy (encryption) protocol identifiers for USM (RFC 3414, RFC 3826).
 *
 * @since 1.0.0
 */
public enum PrivProtocol {

    /**
     * No privacy (no encryption).
     */
    NONE("none", 0, 0),

    /**
     * DES-CBC privacy (RFC 3414).
     * Uses a 16-byte localized key; first 8 bytes are the DES key,
     * last 8 bytes are used as pre-IV.
     */
    DES_CBC("DES/CBC/NoPadding", 16, 8),

    /**
     * AES-128-CFB privacy (RFC 3826).
     * Uses a 16-byte localized key as the AES key.
     */
    AES_128_CFB("AES/CFB/NoPadding", 16, 16);

    private final String algorithm;
    private final int keyLength;
    private final int ivLength;

    PrivProtocol(String algorithm, int keyLength, int ivLength) {
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        this.ivLength = ivLength;
    }

    /**
     * Returns the JCA cipher algorithm string.
     *
     * @return the algorithm name
     */
    public String algorithm() {
        return algorithm;
    }

    /**
     * Returns the key length in bytes.
     *
     * @return the key length
     */
    public int keyLength() {
        return keyLength;
    }

    /**
     * Returns the initialization vector length in bytes.
     *
     * @return the IV length
     */
    public int ivLength() {
        return ivLength;
    }
}
