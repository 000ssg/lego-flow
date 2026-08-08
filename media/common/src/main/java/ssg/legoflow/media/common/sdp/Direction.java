package ssg.legoflow.media.common.sdp;

/**
 * SDP media direction attributes as defined in RFC 4566 section 6.
 *
 * @since 0.1.0
 */
public enum Direction {

    /** Both send and receive (default). */
    SENDRECV("sendrecv"),

    /** Send only. */
    SENDONLY("sendonly"),

    /** Receive only. */
    RECVONLY("recvonly"),

    /** Neither send nor receive (on hold). */
    INACTIVE("inactive");

    private final String token;

    Direction(String token) {
        this.token = token;
    }

    /**
     * Returns the SDP attribute name for this direction.
     *
     * @return the lowercase attribute name
     */
    public String token() {
        return token;
    }

    /**
     * Parses a direction attribute name (case-insensitive).
     *
     * @param token the SDP direction attribute
     * @return the matching direction
     * @throws IllegalArgumentException if the token is unknown
     */
    public static Direction fromToken(String token) {
        for (Direction d : values()) {
            if (d.token.equalsIgnoreCase(token)) {
                return d;
            }
        }
        throw new IllegalArgumentException("Unknown direction: " + token);
    }

    @Override
    public String toString() {
        return token;
    }
}
