package ssg.legoflow.messaging.kafka.client;

import ssg.legoflow.messaging.kafka.common.TopicPartition;

import java.util.Collection;

/**
 * Callback interface for consumer group rebalance events.
 *
 * @since 1.0.0
 */
public interface RebalanceListener {

    /**
     * Called when partitions are assigned to this consumer.
     *
     * @param partitions the newly assigned partitions
     */
    void onPartitionsAssigned(Collection<TopicPartition> partitions);

    /**
     * Called when partitions are revoked from this consumer.
     *
     * @param partitions the revoked partitions
     */
    void onPartitionsRevoked(Collection<TopicPartition> partitions);

    /**
     * Called when partitions are lost (e.g., during cooperative rebalance when
     * partitions are no longer owned without voluntary revocation).
     *
     * <p>Defaults to calling {@link #onPartitionsRevoked(Collection)} for
     * backward compatibility. Cooperative consumers can override this for
     * partition-lost semantics.
     *
     * @param partitions the lost partitions
     * @since 1.0.0
     */
    default void onPartitionsLost(Collection<TopicPartition> partitions) {
        onPartitionsRevoked(partitions);
    }
}
