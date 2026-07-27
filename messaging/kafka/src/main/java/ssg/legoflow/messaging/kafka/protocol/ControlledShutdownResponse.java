package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * ControlledShutdown response (API key 7).
 *
 * @param errorCode           the error code
 * @param partitionsRemaining the partitions still led by the shutting-down broker
 * @since 1.0.0
 */
public record ControlledShutdownResponse(short errorCode,
                                         List<TopicPartitionData> partitionsRemaining) {

    /**
     * A topic-partition still remaining on the broker.
     *
     * @param topic     the topic name
     * @param partition the partition index
     */
    public record TopicPartitionData(String topic, int partition) {
    }
}
