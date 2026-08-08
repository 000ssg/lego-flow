package ssg.legoflow.messaging.kafka.record;

import java.util.List;

/**
 * A Kafka record within a record batch.
 *
 * @param offsetDelta    the offset delta from the batch base offset
 * @param timestampDelta the timestamp delta from the batch base timestamp
 * @param key            the record key (may be null)
 * @param value          the record value (may be null)
 * @param headers        the record headers
 * @since 0.1.0
 */
public record Record(int offsetDelta, long timestampDelta, byte[] key, byte[] value,
                     List<Header> headers) {

    /**
     * Creates a record with default offset/timestamp deltas of 0.
     *
     * @param key     the record key
     * @param value   the record value
     * @param headers the record headers
     */
    public Record(byte[] key, byte[] value, List<Header> headers) {
        this(0, 0L, key, value, headers);
    }

    /**
     * Creates a simple record with key and value only.
     *
     * @param key   the record key
     * @param value the record value
     */
    public Record(byte[] key, byte[] value) {
        this(0, 0L, key, value, List.of());
    }
}
