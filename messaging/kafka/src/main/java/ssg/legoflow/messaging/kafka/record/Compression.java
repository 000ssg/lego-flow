package ssg.legoflow.messaging.kafka.record;

/**
 * Kafka record batch compression types.
 *
 * <p>Only GZIP is supported in this JDK-only implementation.
 * Snappy, LZ4, and ZStandard require native libraries.
 *
 * @since 1.0.0
 */
public enum Compression {

    /** No compression. */
    NONE(0),
    /** GZIP compression (java.util.zip). */
    GZIP(1),
    /** Snappy compression (not supported — requires native library). */
    SNAPPY(2),
    /** LZ4 compression (not supported — requires native library). */
    LZ4(3),
    /** ZStandard compression (not supported — requires native library). */
    ZSTD(4);

    private final int id;

    Compression(int id) {
        this.id = id;
    }

    /**
     * Returns the compression type ID.
     *
     * @return the compression type ID
     */
    public int id() {
        return id;
    }

    /**
     * Returns the compression type for the given ID.
     *
     * @param id the compression type ID
     * @return the compression type
     * @throws IllegalArgumentException if the ID is unknown
     */
    public static Compression forId(int id) {
        for (Compression c : values()) {
            if (c.id == id) return c;
        }
        throw new IllegalArgumentException("Unknown compression type: " + id);
    }
}
