package ssg.legoflow.database.redis.protocol;

/**
 * Redis Serialization Protocol version.
 *
 * <p>RESP2 is the default protocol used by Redis 1.2 through Redis 5.x.
 * RESP3, introduced in Redis 6.0, adds richer type information including
 * maps, sets, doubles, booleans, and nulls as first-class types.
 *
 * @since 0.1.0
 */
public enum RespVersion {

    /** RESP version 2 — default for Redis &lt; 6.0. */
    RESP2(2),

    /** RESP version 3 — available from Redis 6.0 via HELLO command. */
    RESP3(3);

    private final int version;

    RespVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the numeric protocol version.
     *
     * @return 2 or 3
     */
    public int version() {
        return version;
    }

    /**
     * Returns the {@link RespVersion} for the given numeric version.
     *
     * @param version 2 or 3
     * @return the matching enum constant
     * @throws IllegalArgumentException if version is not 2 or 3
     */
    public static RespVersion of(int version) {
        return switch (version) {
            case 2 -> RESP2;
            case 3 -> RESP3;
            default -> throw new IllegalArgumentException("Unsupported RESP version: " + version);
        };
    }
}
