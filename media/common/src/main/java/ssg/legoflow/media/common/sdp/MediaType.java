package ssg.legoflow.media.common.sdp;

/**
 * SDP media types as defined in RFC 4566 section 8.2.1.
 *
 * @since 0.1.0
 */
public enum MediaType {

    /** Audio media. */
    AUDIO("audio"),

    /** Video media. */
    VIDEO("video"),

    /** Text media (e.g., T.140 real-time text). */
    TEXT("text"),

    /** Application media (e.g., BFCP). */
    APPLICATION("application"),

    /** Message media (e.g., MSRP). */
    MESSAGE("message");

    private final String token;

    MediaType(String token) {
        this.token = token;
    }

    /**
     * Returns the SDP token for this media type.
     *
     * @return the lowercase SDP token
     */
    public String token() {
        return token;
    }

    /**
     * Parses a media type token (case-insensitive).
     *
     * @param token the SDP media type token
     * @return the matching media type
     * @throws IllegalArgumentException if the token is unknown
     */
    public static MediaType fromToken(String token) {
        for (MediaType mt : values()) {
            if (mt.token.equalsIgnoreCase(token)) {
                return mt;
            }
        }
        throw new IllegalArgumentException("Unknown media type: " + token);
    }

    @Override
    public String toString() {
        return token;
    }
}
