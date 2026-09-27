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
 * ForgottenTopicsData[](v7+, Topic/TopicId + Partitions[]int32) + RackId(string, v11+).
 *
 * <p>v12 is a flexible-encoding version (compact strings + varint array counts + a trailing
 * tagged section carrying ClusterId:string[12+]) and adds LastFetchedEpoch:int32 to the
 * per-partition layout (after FetchOffset).
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
 * @param rackId           the rack ID of the consumer making this request (v11+; empty string
 *                         when absent — the spec default)
 * @param clusterId        the cluster ID (v12+ tagged field; null when absent — the spec default)
 * @since 0.1.0
 */
public record FetchRequest(int replicaId, int maxWaitMs, int minBytes, int maxBytes,
                           int isolationLevel, int sessionId, int sessionEpoch,
                           List<TopicFetch> topics, List<ForgottenTopic> forgottenTopics,
                           String rackId, String clusterId) {

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
     * {@code sessionEpoch = -1}, {@code forgottenTopics = empty}, the v11+ field defaults to
     * {@code rackId = ""} (the spec absent-value), and the v12+ tagged field defaults to
     * {@code clusterId = null} (the spec absent-value).
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
        this(replicaId, maxWaitMs, minBytes, maxBytes, isolationLevel, 0, -1, topics,
                List.of(), "", null);
    }

    /**
     * Compatibility constructor (pre-v12 call sites): every field carried through v11 with
     * the v12+ tagged field defaulting to {@code clusterId = null} (the spec absent-value).
     *
     * @param replicaId       the broker ID of the follower, or -1 if the request is from a consumer
     * @param maxWaitMs       the maximum time to wait for data in milliseconds
     * @param minBytes        the minimum number of bytes to wait for
     * @param maxBytes        the maximum number of bytes to return (v3+)
     * @param isolationLevel  0=read_committed, 1=read_uncommitted (v4+)
     * @param sessionId       the fetch session ID (v7+; 0 = session creation)
     * @param sessionEpoch    the fetch session epoch (v7+; -1 when creating)
     * @param topics          the topics and partitions to fetch from
     * @param forgottenTopics the topics/partitions being forgotten (v7+; empty when absent)
     * @param rackId          the rack ID of the consumer (v11+; empty string when absent)
     */
    public FetchRequest(int replicaId, int maxWaitMs, int minBytes, int maxBytes, int isolationLevel,
                        int sessionId, int sessionEpoch, List<TopicFetch> topics,
                        List<ForgottenTopic> forgottenTopics, String rackId) {
        this(replicaId, maxWaitMs, minBytes, maxBytes, isolationLevel, sessionId, sessionEpoch,
                topics, forgottenTopics, rackId, null);
    }

    /**
     * Entry point for the {@link Builder}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Hybrid builder for {@link FetchRequest}: the canonical constructor and every compatibility
     * constructor remain; this named-field builder is the preferred entry point for new call
     * sites. Each field defaults to the spec value it takes when its version feature is absent,
     * so a builder sets only the fields a given version carries. {@code build()} delegates to the
     * canonical constructor with no per-version validation — the codec enforces field presence on
     * write and auto-fills absent fields on read.
     */
    public static final class Builder {
        private int replicaId = -1; // -1 = consumer
        private int maxWaitMs = 0;
        private int minBytes = 1;
        private int maxBytes = 0; // v3+; absent default
        private int isolationLevel = 0; // v4+; 0 = read_committed
        private int sessionId = 0; // v7+; 0 = session creation
        private int sessionEpoch = -1; // v7+; -1 when creating
        private List<TopicFetch> topics = List.of();
        private List<ForgottenTopic> forgottenTopics = List.of(); // v7+
        private String rackId = ""; // v11+; "" = absent (spec default)
        private String clusterId; // v12+ tagged field; null = absent (spec default)

        public Builder replicaId(int v) { this.replicaId = v; return this; }
        public Builder maxWaitMs(int v) { this.maxWaitMs = v; return this; }
        public Builder minBytes(int v) { this.minBytes = v; return this; }
        public Builder maxBytes(int v) { this.maxBytes = v; return this; }
        public Builder isolationLevel(int v) { this.isolationLevel = v; return this; }
        public Builder sessionId(int v) { this.sessionId = v; return this; }
        public Builder sessionEpoch(int v) { this.sessionEpoch = v; return this; }
        public Builder topics(List<TopicFetch> v) { this.topics = v; return this; }
        public Builder forgottenTopics(List<ForgottenTopic> v) { this.forgottenTopics = v; return this; }
        public Builder rackId(String v) { this.rackId = v; return this; }
        public Builder clusterId(String v) { this.clusterId = v; return this; }

        public FetchRequest build() {
            return new FetchRequest(replicaId, maxWaitMs, minBytes, maxBytes, isolationLevel,
                    sessionId, sessionEpoch, topics, forgottenTopics, rackId, clusterId);
        }
    }

    /**
     * Per-topic fetch request.
     *
     * <p>v0–v12 carry the topic {@code name}; v13+ replaces it with the 16-byte
     * {@code topicId} (spec: Topic:string is {@code 0-12}, TopicId:uuid is {@code 13+}).
     * A v13+ request encodes only the topic ID; the name is not on the wire.
     *
     * @param name       the topic name (v0–v12; not encoded at v13+)
     * @param topicId    the 16-byte topic UUID (v13+; all zeros when absent — the spec default)
     * @param partitions the partitions to fetch
     */
    public record TopicFetch(String name, byte[] topicId, List<PartitionFetch> partitions) {

        /**
         * Compatibility constructor (pre-v13 call sites): {@code topicId} is all
         * zeros (the v13+ spec default; the value a v0–v12 request carries implicitly).
         *
         * @param name       the topic name
         * @param partitions the partitions to fetch
         */
        public TopicFetch(String name, List<PartitionFetch> partitions) {
            this(name, new byte[16], partitions);
        }

        /**
         * Converts the 16-byte topic ID to its canonical {@code java.util.UUID} form
         * (for diagnostics / logging); the codec works with the raw 16 bytes.
         */
        public java.util.UUID topicUuid() {
            return Uuid.fromBytes(topicId);
        }
    }

    /**
     * A topic/partition being forgotten for a fetch session (v7+).
     *
     * <p>Wire layout (v7–v12): Topic:string + Partitions:[]int32 — an empty partitions list
     * means the whole topic is forgotten. v13+ replaces Topic:string with
     * TopicId:uuid (16 bytes); the name is not on the wire.
     *
     * @param name       the topic name (v7–v12; not encoded at v13+)
     * @param topicId    the 16-byte topic UUID (v13+; all zeros when absent — the spec default)
     * @param partitions the partition indexes to forget (an empty list means the whole topic)
     */
    public record ForgottenTopic(String name, byte[] topicId, List<Integer> partitions) {

        /**
         * Compatibility constructor (pre-v13 call sites): {@code topicId} is all
         * zeros (the v13+ spec default).
         *
         * @param name       the topic name
         * @param partitions the partition indexes to forget
         */
        public ForgottenTopic(String name, List<Integer> partitions) {
            this(name, new byte[16], partitions);
        }

        /**
         * Converts the 16-byte topic ID to its canonical {@code java.util.UUID} form
         * (for diagnostics / logging); the codec works with the raw 16 bytes.
         */
        public java.util.UUID topicUuid() {
            return Uuid.fromBytes(topicId);
        }
    }

    /**
     * Per-partition fetch request.
     *
     * <p>Wire order per the 3.6.1 spec: Partition, CurrentLeaderEpoch (v9+), FetchOffset,
     * LastFetchedEpoch (v12+), LogStartOffset (v5+), PartitionMaxBytes.
     *
     * @param partition           the partition index (v0+)
     * @param currentLeaderEpoch  the current leader epoch of the partition (v9+; -1 when absent
     *                            / unknown — the spec default; the value a consumer never sends)
     * @param fetchOffset         the offset to start fetching from (v0+)
     * @param lastFetchedEpoch    the epoch at the last fetched offset (v12+; -1 when absent —
     *                            the spec default; the value a consumer never sends)
     * @param partitionMaxBytes   the maximum bytes per partition (v0+)
     * @param logStartOffset      the earliest available offset of the follower replica; the field is
     *                            only used when the request is sent by the follower (v5+; absent from
     *                            v0–v4 bodies, decoded as -1 there)
     */
    public record PartitionFetch(int partition, int currentLeaderEpoch, long fetchOffset,
                                 int lastFetchedEpoch, int partitionMaxBytes, long logStartOffset) {

        /**
         * Compatibility constructor (pre-v12 call sites): {@code lastFetchedEpoch = -1}
         * (the v12+ spec default; the value a consumer never sends).
         *
         * @param partition           the partition index
         * @param currentLeaderEpoch  the current leader epoch of the partition (v9+)
         * @param fetchOffset         the offset to start fetching from
         * @param partitionMaxBytes   the maximum bytes per partition
         * @param logStartOffset      the earliest available offset of the follower replica (v5+)
         */
        public PartitionFetch(int partition, int currentLeaderEpoch, long fetchOffset,
                              int partitionMaxBytes, long logStartOffset) {
            this(partition, currentLeaderEpoch, fetchOffset, -1, partitionMaxBytes, logStartOffset);
        }

        /**
         * Compatibility constructor (pre-v9 call sites): {@code currentLeaderEpoch = -1}
         * (the v9+ spec default; the value a consumer never sends) and
         * {@code lastFetchedEpoch = -1} (the v12+ spec default).
         *
         * @param partition        the partition index
         * @param fetchOffset      the offset to start fetching from
         * @param partitionMaxBytes the maximum bytes per partition
         * @param logStartOffset   the earliest available offset of the follower replica (v5+)
         */
        public PartitionFetch(int partition, long fetchOffset, int partitionMaxBytes,
                              long logStartOffset) {
            this(partition, -1, fetchOffset, -1, partitionMaxBytes, logStartOffset);
        }

        /**
         * Compatibility constructor (pre-v5 call sites): {@code currentLeaderEpoch = -1}
         * (v9+), {@code lastFetchedEpoch = -1} (v12+), and {@code logStartOffset = -1}
         * (v5+ spec defaults).
         *
         * @param partition        the partition index
         * @param fetchOffset      the offset to start fetching from
         * @param partitionMaxBytes the maximum bytes per partition
         */
        public PartitionFetch(int partition, long fetchOffset, int partitionMaxBytes) {
            this(partition, -1, fetchOffset, -1, partitionMaxBytes, -1L);
        }

        /**
         * Entry point for the {@link Builder}.
         */
        public static Builder builder() {
            return new Builder();
        }

        /**
         * Hybrid builder for {@link PartitionFetch}; defaults are the spec absent-values
         * ({@code currentLeaderEpoch = -1}, {@code lastFetchedEpoch = -1},
         * {@code logStartOffset = -1}), so a builder sets only the fields a given version carries.
         */
        public static final class Builder {
            private int partition = 0;
            private int currentLeaderEpoch = -1; // v9+; -1 = unknown (consumer default)
            private long fetchOffset = 0;
            private int lastFetchedEpoch = -1; // v12+; -1 = unknown (consumer default)
            private int partitionMaxBytes = 0;
            private long logStartOffset = -1L; // v5+; -1 = absent

            public Builder partition(int v) { this.partition = v; return this; }
            public Builder currentLeaderEpoch(int v) { this.currentLeaderEpoch = v; return this; }
            public Builder fetchOffset(long v) { this.fetchOffset = v; return this; }
            public Builder lastFetchedEpoch(int v) { this.lastFetchedEpoch = v; return this; }
            public Builder partitionMaxBytes(int v) { this.partitionMaxBytes = v; return this; }
            public Builder logStartOffset(long v) { this.logStartOffset = v; return this; }

            public PartitionFetch build() {
                return new PartitionFetch(partition, currentLeaderEpoch, fetchOffset,
                        lastFetchedEpoch, partitionMaxBytes, logStartOffset);
            }
        }
    }
}
