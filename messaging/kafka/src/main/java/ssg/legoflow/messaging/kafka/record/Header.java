package ssg.legoflow.messaging.kafka.record;

/**
 * A Kafka record header (key-value pair).
 *
 * @param key   the header key
 * @param value the header value (may be null)
 * @since 0.1.0
 */
public record Header(String key, byte[] value) {

    /**
     * Creates a header with a string value.
     *
     * @param key   the header key
     * @param value the string value
     * @return the header
     */
    public static Header of(String key, String value) {
        return new Header(key, value != null ? value.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null);
    }
}
