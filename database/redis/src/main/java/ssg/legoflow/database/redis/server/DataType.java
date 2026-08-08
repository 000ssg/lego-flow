package ssg.legoflow.database.redis.server;

/**
 * Internal Redis data types.
 *
 * @since 0.1.0
 */
public enum DataType {

    /** String value (byte array). */
    STRING("string"),

    /** Linked list of strings. */
    LIST("list"),

    /** Unordered set of unique strings. */
    SET("set"),

    /** Sorted set with score-member pairs. */
    ZSET("zset"),

    /** Hash map of field-value pairs. */
    HASH("hash"),

    /** Append-only log with consumer groups. */
    STREAM("stream"),

    /** Probabilistic cardinality estimation. */
    HYPERLOGLOG("string"),

    /** No type (key does not exist). */
    NONE("none");

    private final String typeName;

    DataType(String typeName) {
        this.typeName = typeName;
    }

    /**
     * Returns the Redis TYPE command response string.
     *
     * @return type name as returned by TYPE command
     */
    public String typeName() {
        return typeName;
    }
}
