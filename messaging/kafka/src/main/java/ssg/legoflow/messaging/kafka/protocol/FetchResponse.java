package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Fetch response (API key 1).
 *
 * <p>Layout per the 3.6.1 spec ({@code doc/spec/message/FetchResponse.json}):
 * ThrottleTimeMs(int32, 1+) + ErrorCode(int16, 7+) + SessionId(int32, 7+)
 * + Topics[](Topic/TopicId + Partitions[](PartitionIndex, ErrorCode, HighWatermark,
 * LastStableOffset 4+, LogStartOffset 5+, AbortedTransactions 4+, PreferredReadReplica 11+,
 * Records)).
 *
 * @param throttleTimeMs the throttle time in milliseconds (v1+)
 * @param errorCode      the top-level error code of the whole response (v7+; 0 when absent)
 * @param sessionId      the fetch session ID, echoed back by the broker (v7+; 0 when absent)
 * @param topics         the per-topic responses
 * @since 0.1.0
 */
public record FetchResponse(int throttleTimeMs, short errorCode, int sessionId,
                            List<TopicResponse> topics) {

    /**
     * Compatibility constructor (pre-v7 call sites): the v7+ fields default to
     * {@code errorCode = 0} (NO_ERROR) and {@code sessionId = 0}.
     *
     * @param throttleTimeMs the throttle time in milliseconds (v1+)
     * @param topics         the per-topic responses
     */
    public FetchResponse(int throttleTimeMs, List<TopicResponse> topics) {
        this(throttleTimeMs, (short) 0, 0, topics);
    }

    /**
     * Entry point for the {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Hybrid builder for {@link FetchResponse}: the canonical and compatibility constructors
     * remain; this named-field builder is the preferred entry point for new call sites. Each field
     * defaults to the spec absent-value (top-level {@code errorCode = 0}, {@code sessionId = 0}
     * are the v7+ defaults), so a builder sets only the fields a given version carries.
     * {@code build()} delegates to the canonical constructor with no per-version validation.
     */
    public static final class Builder {
        private int throttleTimeMs = 0; // v1+; absent default
        private short errorCode = 0; // v7+; absent default
        private int sessionId = 0; // v7+; absent default
        private List<TopicResponse> topics = List.of();

        public Builder throttleTimeMs(int v) { this.throttleTimeMs = v; return this; }
        public Builder errorCode(short v) { this.errorCode = v; return this; }
        public Builder sessionId(int v) { this.sessionId = v; return this; }
        public Builder topics(List<TopicResponse> v) { this.topics = v; return this; }

        public FetchResponse build() {
            return new FetchResponse(throttleTimeMs, errorCode, sessionId, topics);
        }
    }

    /**
     * Per-topic fetch response.
     *
     * @param name       the topic name (v0–v12; replaced by TopicId at v13+)
     * @param partitions the per-partition responses
     */
    public record TopicResponse(String name, List<PartitionResponse> partitions) {
    }

    /**
     * Per-partition fetch response.
     *
     * <p>Wire order per the 3.6.1 spec: PartitionIndex, ErrorCode, HighWatermark,
     * LastStableOffset (v4+), LogStartOffset (v5+), AbortedTransactions (v4+),
     * PreferredReadReplica (v11+), Records.
     *
     * @param partitionIndex    the partition index
     * @param errorCode         the error code
     * @param highWatermark     the high watermark offset
     * @param lastStableOffset  the last stable offset (v4+; -1 when absent / no aborted transactions)
     * @param logStartOffset    the current log start offset (v5+; -1 when absent)
     * @param abortedTransactions aborted-transaction offsets (v4+; null when absent / empty)
     * @param preferredReadReplica the preferred read replica for the consumer's next fetch
     *                            (v11+; -1 when absent — the spec default)
     * @param records           the raw record batch bytes (may be null)
     */
    public record PartitionResponse(int partitionIndex, short errorCode, long highWatermark,
                                    long lastStableOffset, long logStartOffset,
                                    List<AbortedTransaction> abortedTransactions,
                                    int preferredReadReplica, byte[] records) {

        /**
         * Compatibility constructor (pre-v5 call sites): {@code lastStableOffset = -1},
         * {@code logStartOffset = -1}, {@code abortedTransactions = null}
         * (all three fields are v4+/v5+), and {@code preferredReadReplica = -1} (v11+).
         *
         * @param partitionIndex the partition index
         * @param errorCode      the error code
         * @param highWatermark  the high watermark offset
         * @param records        the raw record batch bytes (may be null)
         */
        public PartitionResponse(int partitionIndex, short errorCode, long highWatermark, byte[] records) {
            this(partitionIndex, errorCode, highWatermark, -1L, -1L, null, -1, records);
        }

        /**
         * Compatibility constructor (v4 call sites): {@code logStartOffset = -1}
         * (the v5+ field, absent from v4 bodies) and {@code preferredReadReplica = -1}
         * (the v11+ field).
         *
         * @param partitionIndex    the partition index
         * @param errorCode         the error code
         * @param highWatermark     the high watermark offset
         * @param lastStableOffset  the last stable offset
         * @param abortedTransactions aborted-transaction offsets (may be null)
         * @param records           the raw record batch bytes (may be null)
         */
        public PartitionResponse(int partitionIndex, short errorCode, long highWatermark,
                                 long lastStableOffset, List<AbortedTransaction> abortedTransactions,
                                 byte[] records) {
            this(partitionIndex, errorCode, highWatermark, lastStableOffset, -1L, abortedTransactions, -1, records);
        }

        /**
         * Entry point for the {@link Builder}.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Hybrid builder for {@link PartitionResponse}; defaults are the spec absent-values
         * ({@code lastStableOffset = -1}, {@code logStartOffset = -1},
         * {@code abortedTransactions = null}, {@code preferredReadReplica = -1}), so a builder
         * sets only the fields a given version carries.
         */
        public static final class Builder {
            private int partitionIndex = 0;
            private short errorCode = 0;
            private long highWatermark = 0;
            private long lastStableOffset = -1L; // v4+; absent default
            private long logStartOffset = -1L; // v5+; absent default
            private List<AbortedTransaction> abortedTransactions; // v4+; null = absent
            private int preferredReadReplica = -1; // v11+; -1 = absent
            private byte[] records;

            public Builder partitionIndex(int v) { this.partitionIndex = v; return this; }
            public Builder errorCode(short v) { this.errorCode = v; return this; }
            public Builder highWatermark(long v) { this.highWatermark = v; return this; }
            public Builder lastStableOffset(long v) { this.lastStableOffset = v; return this; }
            public Builder logStartOffset(long v) { this.logStartOffset = v; return this; }
            public Builder abortedTransactions(List<AbortedTransaction> v) { this.abortedTransactions = v; return this; }
            public Builder preferredReadReplica(int v) { this.preferredReadReplica = v; return this; }
            public Builder records(byte[] v) { this.records = v; return this; }

            public PartitionResponse build() {
                return new PartitionResponse(partitionIndex, errorCode, highWatermark,
                        lastStableOffset, logStartOffset, abortedTransactions, preferredReadReplica, records);
            }
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
