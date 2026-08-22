package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.record.Header;
import java.nio.charset.StandardCharsets;
import java.util.List;
/**
 * A record consumed from Kafka.
 *
 * @param topic     the topic
 * @param partition the partition
 * @param offset    the offset
 * @param key       the record key (may be null)
 * @param value     the record value (may be null)
 * @param headers   the record headers
 * @since 0.1.0
 */
public record ConsumerRecord(String topic, int partition, long offset,
                             byte[] key, byte[] value, List<Header> headers) {

    /**
     * Returns the key as a string.
     *
     * @return the key string, or null
     */
    public String keyAsString() {
        return key != null ? new String(key, StandardCharsets.UTF_8) : null;
    }

    /**
     * Returns the value as a string.
     *
     * @return the value string, or null
     */
    public String valueAsString() {
        return value != null ? new String(value, StandardCharsets.UTF_8) : null;
    }

    @Override
    public String toString() {
        return "ConsumerRecord{topic=" + topic + ", partition=" + partition + ", offset=" + offset
                + ", key=" + keyAsString() + ", value=" + valueAsString() + "}";
    }
}
