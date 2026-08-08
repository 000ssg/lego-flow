package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Fetch request (API key 1) for consuming records.
 *
 * @param maxWaitMs  the maximum time to wait for data in milliseconds
 * @param minBytes   the minimum number of bytes to wait for
 * @param maxBytes   the maximum number of bytes to return
 * @param topics     the topics and partitions to fetch from
 * @since 0.1.0
 */
public record FetchRequest(int maxWaitMs, int minBytes, int maxBytes, List<TopicFetch> topics) {

    /**
     * Per-topic fetch request.
     *
     * @param name       the topic name
     * @param partitions the partitions to fetch
     */
    public record TopicFetch(String name, List<PartitionFetch> partitions) {
    }

    /**
     * Per-partition fetch request.
     *
     * @param partition        the partition index
     * @param fetchOffset      the offset to start fetching from
     * @param partitionMaxBytes the maximum bytes per partition
     */
    public record PartitionFetch(int partition, long fetchOffset, int partitionMaxBytes) {
    }
}
