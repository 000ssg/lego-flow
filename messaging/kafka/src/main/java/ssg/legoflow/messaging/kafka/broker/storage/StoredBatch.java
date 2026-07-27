package ssg.legoflow.messaging.kafka.broker.storage;

/**
 * A stored record batch with its base offset and raw bytes.
 *
 * <p>Represents a single record batch as persisted in a partition log.
 * The {@code data} field contains the Kafka v2 encoded batch bytes,
 * and {@code baseOffset} is the absolute offset of the first record
 * in the batch.
 *
 * @param baseOffset   the absolute offset of the first record in the batch
 * @param recordCount  the number of records in the batch
 * @param data         the raw Kafka v2 encoded batch bytes
 * @param timestamp    the wall-clock time when the batch was stored (milliseconds since epoch)
 * @since 1.0.0
 */
public record StoredBatch(long baseOffset, int recordCount, byte[] data, long timestamp) {
}
