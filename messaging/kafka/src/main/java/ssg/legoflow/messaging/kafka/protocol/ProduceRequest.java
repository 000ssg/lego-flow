package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Produce request (API key 0) for publishing records.
 *
 * @param transactionalId the transactional ID (nullable)
 * @param acks            the number of acknowledgments required (-1=all, 0=none, 1=leader)
 * @param timeoutMs       the timeout in milliseconds
 * @param topicData       the data to produce per topic
 * @since 0.1.0
 */
public record ProduceRequest(String transactionalId, short acks, int timeoutMs,
                             List<TopicData> topicData) {

    /**
     * Entry point for the {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Hybrid builder for {@link ProduceRequest}: the canonical constructor remains; this
     * named-field builder is the preferred entry point for new call sites. Defaults are the spec
     * values a client omits ({@code transactionalId = null} for non-transactional produce,
     * {@code topicData = empty}). {@code build()} delegates to the canonical constructor.
     */
    public static final class Builder {
        private String transactionalId; // null = non-transactional
        private short acks = 1;
        private int timeoutMs = 30000;
        private List<TopicData> topicData = List.of();

        public Builder transactionalId(String v) { this.transactionalId = v; return this; }
        public Builder acks(short v) { this.acks = v; return this; }
        public Builder timeoutMs(int v) { this.timeoutMs = v; return this; }
        public Builder topicData(List<TopicData> v) { this.topicData = v; return this; }

        public ProduceRequest build() {
            return new ProduceRequest(transactionalId, acks, timeoutMs, topicData);
        }
    }

    /**
     * Per-topic produce data.
     *
     * @param name           the topic name
     * @param partitionData  the data per partition
     */
    public record TopicData(String name, List<PartitionData> partitionData) {
    }

    /**
     * Per-partition produce data.
     *
     * @param index      the partition index
     * @param records    the raw record batch bytes
     */
    public record PartitionData(int index, byte[] records) {
    }
}
