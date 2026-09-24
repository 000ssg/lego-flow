package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Produce response (API key 0).
 *
 * @param responses    the per-topic responses
 * @param throttleTimeMs the throttle time in milliseconds
 * @since 0.1.0
 */
public record ProduceResponse(List<TopicResponse> responses, int throttleTimeMs) {

    /**
     * Per-topic response.
     *
     * @param name               the topic name
     * @param partitionResponses the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitionResponses) {
    }

    /**
     * Per-partition response.
     *
     * @param partitionIndex   the partition index
     * @param errorCode        the error code
     * @param baseOffset       the base offset of the appended records
     * @param logAppendTimeMs  the log append time of the appended records in milliseconds
     *                         (v2+, spec default -1)
     * @param logStartOffset   the log start offset of the partition (v5+, spec default -1
     *                         when the broker has no value)
     * @param recordErrors     the per-batch error entries (v8+, non-null array — empty when
     *                         the broker reports none; ignorable)
     * @param errorMessage     the global error message summarizing the common root cause of
     *                         the dropped records (v8+, nullable; ignorable)
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long baseOffset,
                                    long logAppendTimeMs, long logStartOffset,
                                    List<BatchIndexAndErrorMessage> recordErrors,
                                    String errorMessage) {

        /**
         * A batch index paired with its error message (v8+).
         *
         * @param batchIndex            the batch index of the record that caused the batch to
         *                              be dropped
         * @param batchIndexErrorMessage the error message of the record that caused the batch
         *                               to be dropped (nullable)
         */
        public record BatchIndexAndErrorMessage(int batchIndex, String batchIndexErrorMessage) {
        }

        /**
         * Compatibility constructor for v5 responses (no record errors / error message).
         */
        public PartitionResponse(int partitionIndex, short errorCode, long baseOffset,
                                 long logAppendTimeMs, long logStartOffset) {
            this(partitionIndex, errorCode, baseOffset, logAppendTimeMs, logStartOffset, null, null);
        }

        /**
         * Compatibility constructor for v0–v4 responses.
         */
        public PartitionResponse(int partitionIndex, short errorCode, long baseOffset,
                                 long logAppendTimeMs) {
            this(partitionIndex, errorCode, baseOffset, logAppendTimeMs, -1L, null, null);
        }
    }
}
