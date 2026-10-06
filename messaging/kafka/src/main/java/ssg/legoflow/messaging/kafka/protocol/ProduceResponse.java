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

        /**
         * Entry point for the {@link Builder}.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Hybrid builder for {@link PartitionResponse}; defaults are the spec absent-values
         * ({@code logAppendTimeMs = -1} is the v2+ default, {@code logStartOffset = -1} is the v5+
         * default, {@code recordErrors = null} / {@code errorMessage = null} for pre-v8). A builder
         * sets only the fields a given version carries.
         */
        public static final class Builder {
            private int partitionIndex = 0;
            private short errorCode = 0;
            private long baseOffset = 0;
            private long logAppendTimeMs = -1L; // v2+; absent default
            private long logStartOffset = -1L; // v5+; absent default
            private List<BatchIndexAndErrorMessage> recordErrors; // v8+; null = absent
            private String errorMessage; // v8+; null = absent

            public Builder partitionIndex(int v) { this.partitionIndex = v; return this; }
            public Builder errorCode(short v) { this.errorCode = v; return this; }
            public Builder baseOffset(long v) { this.baseOffset = v; return this; }
            public Builder logAppendTimeMs(long v) { this.logAppendTimeMs = v; return this; }
            public Builder logStartOffset(long v) { this.logStartOffset = v; return this; }
            public Builder recordErrors(List<BatchIndexAndErrorMessage> v) { this.recordErrors = v; return this; }
            public Builder errorMessage(String v) { this.errorMessage = v; return this; }

            public PartitionResponse build() {
                return new PartitionResponse(partitionIndex, errorCode, baseOffset,
                        logAppendTimeMs, logStartOffset, recordErrors, errorMessage);
            }
        }
    }
}
