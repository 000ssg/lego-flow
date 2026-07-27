package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Metadata response (API key 3).
 *
 * @param brokers the list of brokers
 * @param topics  the list of topic metadata
 * @since 1.0.0
 */
public record MetadataResponse(List<BrokerMetadata> brokers, List<TopicMetadata> topics) {

    /**
     * Broker metadata.
     *
     * @param nodeId the broker ID
     * @param host   the broker hostname
     * @param port   the broker port
     */
    public record BrokerMetadata(int nodeId, String host, int port) {
    }

    /**
     * Topic metadata.
     *
     * @param errorCode  the error code for this topic
     * @param name       the topic name
     * @param partitions the list of partition metadata
     */
    public record TopicMetadata(short errorCode, String name, List<PartitionMetadata> partitions) {
    }

    /**
     * Partition metadata.
     *
     * @param errorCode  the error code for this partition
     * @param partitionIndex the partition index
     * @param leaderId   the leader broker ID
     * @param replicaIds the replica broker IDs
     * @param isrIds     the in-sync replica broker IDs
     */
    public record PartitionMetadata(short errorCode, int partitionIndex, int leaderId,
                                    List<Integer> replicaIds, List<Integer> isrIds) {
    }
}
