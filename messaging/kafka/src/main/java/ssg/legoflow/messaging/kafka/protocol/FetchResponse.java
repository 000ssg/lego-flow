package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Fetch response (API key 1).
 *
 * @param throttleTimeMs the throttle time in milliseconds (v1+)
 * @param topics         the per-topic responses
 * @since 0.1.0
 */
public record FetchResponse(int throttleTimeMs, List<TopicResponse> topics) {

    /**
     * Per-topic fetch response.
     *
     * @param name       the topic name
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition fetch response.
     *
 * <p>Wire order per the 3.6.1 spec: PartitionIndex, ErrorCode, HighWatermark,
     * LastStableOffset (v4+), AbortedTransactions (v4+), Records.
     *
     * @param partitionIndex    the partition index
     * @param errorCode         the error code
     * @param highWatermark     the high watermark offset
     * @param lastStableOffset  the last stable offset (v4+; -1 when absent / no aborted transactions)
     * @param abortedTransactions aborted-transaction offsets (v4+; null when absent / empty)
     * @param records           the raw record batch bytes (may be null)
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long highWatermark,
                                    long lastStableOffset, List<AbortedTransaction> abortedTransactions,
                                    byte[] records) {

        /**
         * Compatibility constructor (pre-v4 call sites): {@code lastStableOffset = -1},
         * {@code abortedTransactions = null} (both fields are v4+).
         *
         * @param partitionIndex the partition index
         * @param errorCode      the error code
         * @param highWatermark  the high watermark offset
         * @param records        the raw record batch bytes (may be null)
         */
        public PartitionResponse(int partitionIndex, short errorCode, long highWatermark, byte[] records) {
            this(partitionIndex, errorCode, highWatermark, -1L, null, records);
        }
    }

    /**
     * An aborted-transaction offset (v4+).
     *
     * @param producerId  the producer ID that generated the transaction
     * @param firstOffset the first offset of the aborted transaction
     */
    public record AbortedTransaction(long producerId, long firstOffset) {
    }
}
