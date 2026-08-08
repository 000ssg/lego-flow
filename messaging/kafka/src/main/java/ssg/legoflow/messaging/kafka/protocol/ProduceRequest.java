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
