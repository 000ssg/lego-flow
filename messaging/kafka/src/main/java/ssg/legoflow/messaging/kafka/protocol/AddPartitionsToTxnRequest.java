package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * AddPartitionsToTxn request (API key 24).
 *
 * @param transactionalId the transactional ID
 * @param producerId      the producer ID
 * @param producerEpoch   the producer epoch
 * @param topics          the topics and partitions to add to the transaction
 * @since 1.0.0
 */
public record AddPartitionsToTxnRequest(String transactionalId, long producerId, short producerEpoch,
                                        List<TopicPartitions> topics) {

    /**
     * Per-topic partition list.
     *
     * @param name             the topic name
     * @param partitionIndexes the partition indexes
     */
    public record TopicPartitions(String name, List<Integer> partitionIndexes) {
    }
}
