package ssg.legoflow.messaging.kafka.protocol;

import java.util.List;

/**
 * Fetch request (API key 1) for consuming records.
 *
 * <p>Layout per the 3.6.1 spec ({@code doc/spec/message/FetchRequest.json}):
 * ReplicaId(int32, 0-14) + MaxWaitMs(int32, 0+) + MinBytes(int32, 0+) + MaxBytes(int32, 3+)
 * + IsolationLevel(int8, 4+) + SessionId(int32, 7+) + SessionEpoch(int32, 7+)
 * + Topics[](Topic/TopicId + Partitions[](Partition, CurrentLeaderEpoch 9+, FetchOffset,
 * LastFetchedEpoch 12+, LogStartOffset 5+, PartitionMaxBytes)) +
 * ForgottenTopicsData[](v7+, Topic/TopicId + Partitions[]int32).
 *
 * @param replicaId        the broker ID of the follower, or -1 if the request is from a consumer (v0+)
 * @param maxWaitMs        the maximum time to wait for data in milliseconds (v0+)
 * @param minBytes         the minimum number of bytes to wait for (v0+)
 * @param maxBytes         the maximum number of bytes to return (v3+; absent from v0–v2 bodies)
 * @param isolationLevel   0=read_committed, 1=read_uncommitted (v4+; absent from v0–v3 bodies)
 * @param sessionId        the fetch session ID; 0 means the session is being created or invalidated (v7+; absent from v0–v6 bodies)
 * @param sessionEpoch     the fetch session epoch; -1 when the session is being created (v7+; absent from v0–v6 bodies)
 * @param topics           the topics and partitions to fetch from (v0+)
 * @param forgottenTopics  the topics/partitions being forgotten for this session (v7+; empty when absent)
 * @since 0.1.0
 */
public record FetchRequest(int replicaId, int maxWaitMs, int minBytes, int maxBytes,
                           int isolationLevel, int sessionId, int sessionEpoch,
                           List<TopicFetch> topics, List<ForgottenTopic> forgottenTopics) {

    /**
     * Compatibility constructor (pre-v4 call sites): consumer fetch with {@code replicaId = -1}
     * and {@code isolationLevel = 0} (read_committed, the v4+ spec default).
     *
     * @param maxWaitMs  the maximum time to wait for data in milliseconds
     * @param minBytes   the minimum number of bytes to wait for
     * @param maxBytes   the maximum number of bytes to return (v3+; discarded at v0–v2)
     * @param topics     the topics and partitions to fetch from
     */
    public FetchRequest(int maxWaitMs, int minBytes, int maxBytes, List<TopicFetch> topics) {
        this(-1, maxWaitMs, minBytes, maxBytes, 0, topics);
    }

    /**
     * Compatibility constructor (pre-v4 call sites with an explicit replicaId):
     * {@code isolationLevel = 0}.
     *
     * @param replicaId  the broker ID of the follower, or -1 if the request is from a consumer
     * @param maxWaitMs  the maximum time to wait for data in milliseconds
     * @param minBytes   the minimum number of bytes to wait for
     * @param maxBytes   the maximum number of bytes to return (v3+; discarded at v0–v2)
     * @param topics     the topics and partitions to fetch from
     */
    public FetchRequest(int replicaId, int maxWaitMs, int minBytes, int maxBytes, List<TopicFetch> topics) {
        this(replicaId, maxWaitMs, minBytes, maxBytes, 0, topics);
    }

    /**
     * Compatibility constructor (pre-v7 call sites): {@code isolationLevel} carried, but the
     * v7+ session fields default to {@code sessionId = 0} (session creation),
     * {@code sessionEpoch = -1}, and {@code forgottenTopics = empty}.
     *
     * @param replicaId      the broker ID of the follower, or -1 if the request is from a consumer
     * @param maxWaitMs      the maximum time to wait for data in milliseconds
     * @param minBytes       the minimum number of bytes to wait for
     * @param maxBytes       the maximum number of bytes to return (v3+)
     * @param isolationLevel 0=read_committed, 1=read_uncommitted (v4+)
     * @param topics         the topics and partitions to fetch from
     */
    public FetchRequest(int replicaId, int maxWaitMs, int minBytes, int maxBytes, int isolationLevel,
                        List<TopicFetch> topics) {
        this(replicaId, maxWaitMs, minBytes, maxBytes, isolationLevel, 0, -1, topics, List.of());
    }

    /**
     * Per-topic fetch request.
     *
     * @param name       the topic name (v0–v12; replaced by TopicId at v13+)
     * @param partitions the partitions to fetch
     */
    public record TopicFetch(String name, List<PartitionFetch> partitions) {
    }

    /**
     * A topic/partition being forgotten for a fetch session (v7+).
     *
     * <p>Wire layout (v7–v12): Topic:string + Partitions:[]int32 — an empty partitions list
     * means the whole topic is forgotten.
     *
     * @param name       the topic name (v7–v12; replaced by TopicId at v13+)
     * @param partitions the partition indexes to forget (an empty list means the whole topic)
     */
    public record ForgottenTopic(String name, List<Integer> partitions) {
    }

    /**
     * Per-partition fetch request.
     *
     * <p>Wire order per the 3.6.1 spec: Partition, CurrentLeaderEpoch (v9+), FetchOffset,
     * LastFetchedEpoch (v12+), LogStartOffset (v5+), PartitionMaxBytes.
     *
     * @param partition        the partition index (v0+)
     * @param fetchOffset      the offset to start fetching from (v0+)
     * @param partitionMaxBytes the maximum bytes per partition (v0+)
     * @param logStartOffset   the earliest available offset of the follower replica; the field is
     *                         only used when the request is sent by the follower (v5+; absent from
     *                         v0–v4 bodies, decoded as -1 there)
     */
    public record PartitionFetch(int partition, long fetchOffset, int partitionMaxBytes,
                                 long logStartOffset) {

        /**
         * Compatibility constructor (pre-v5 call sites): {@code logStartOffset = -1}
         * (the spec default; the value a consumer never sends).
         *
         * @param partition        the partition index
         * @param fetchOffset      the offset to start fetching from
         * @param partitionMaxBytes the maximum bytes per partition
         */
        public PartitionFetch(int partition, long fetchOffset, int partitionMaxBytes) {
            this(partition, fetchOffset, partitionMaxBytes, -1L);
        }
    }
}
