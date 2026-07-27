package ssg.legoflow.network.snmp.security;

/**
 * Authentication protocol identifiers for USM (RFC 3414).
 *
 * @since 1.0.0
 */
public enum AuthProtocol {

    /**
     * No authentication.
     */
    NONE("none", 0, 0),

    /**
     * HMAC-MD5-96 authentication (RFC 3414).
     * Produces a 12-byte truncated HMAC-MD5 digest.
     */
    HMAC_MD5_96("HmacMD5", 16, 12),

    /**
     * HMAC-SHA-96 authentication (RFC 3414).
     * Produces a 12-byte truncated HMAC-SHA-1 digest.
     */
    HMAC_SHA_96("HmacSHA1", 20, 12);

    private final String algorithm;
    private final int keyLength;
    private final int truncatedLength;

    AuthProtocol(String algorithm, int keyLength, int truncatedLength) {
        this.algorithm = algorithm;
        this.keyLength = keyLength;
        this.truncatedLength = truncatedLength;
    }

    /**
     * Returns the JCA algorithm name for HMAC computation.
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
     * Returns the truncated digest length in bytes (12 for both MD5 and SHA).
     *
     * @return the truncated length
     */
    public int truncatedLength() {
        return truncatedLength;
    }

    /**
     * Returns the hash algorithm name for key localization.
     *
     * @return the hash algorithm (MD5 or SHA-1)
     */
    public String hashAlgorithm() {
        return switch (this) {
            case HMAC_MD5_96 -> "MD5";
            case HMAC_SHA_96 -> "SHA-1";
            case NONE -> throw new UnsupportedOperationException("No hash for NONE auth");
        };
    }
}
