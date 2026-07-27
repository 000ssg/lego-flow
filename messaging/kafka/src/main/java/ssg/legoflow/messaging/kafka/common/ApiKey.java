package ssg.legoflow.messaging.kafka.common;

/**
 * Kafka API keys defining all supported request types.
 *
 * @since 1.0.0
 */
public enum ApiKey {

    /** Publish records to a topic partition. */
    PRODUCE(0, "Produce", 0, 9),
    /** Fetch records from topic partitions. */
    FETCH(1, "Fetch", 0, 13),
    /** List offsets for topic partitions. */
    LIST_OFFSETS(2, "ListOffsets", 0, 7),
    /** Get topic/partition/broker metadata. */
    METADATA(3, "Metadata", 0, 12),
    /** Controller to broker: assign partition leadership and ISR. */
    LEADER_AND_ISR(4, "LeaderAndIsr", 0, 5),
    /** Controller to broker: stop replicating partitions. */
    STOP_REPLICA(5, "StopReplica", 0, 3),
    /** Controller to broker: update metadata cache. */
    UPDATE_METADATA(6, "UpdateMetadata", 0, 7),
    /** Broker to controller: request graceful shutdown. */
    CONTROLLED_SHUTDOWN(7, "ControlledShutdown", 0, 3),
    /** Commit consumer group offsets. */
    OFFSET_COMMIT(8, "OffsetCommit", 0, 8),
    /** Fetch committed consumer group offsets. */
    OFFSET_FETCH(9, "OffsetFetch", 0, 8),
    /** Find the coordinator for a group or transactional ID. */
    FIND_COORDINATOR(10, "FindCoordinator", 0, 4),
    /** Join a consumer group. */
    JOIN_GROUP(11, "JoinGroup", 0, 9),
    /** Send heartbeat to keep consumer group membership alive. */
    HEARTBEAT(12, "Heartbeat", 0, 4),
    /** Leave a consumer group. */
    LEAVE_GROUP(13, "LeaveGroup", 0, 5),
    /** Synchronize consumer group partition assignments. */
    SYNC_GROUP(14, "SyncGroup", 0, 5),
    /** Describe consumer groups. */
    DESCRIBE_GROUPS(15, "DescribeGroups", 0, 5),
    /** List consumer groups. */
    LIST_GROUPS(16, "ListGroups", 0, 4),
    /** Negotiate SASL authentication mechanism. */
    SASL_HANDSHAKE(17, "SaslHandshake", 0, 1),
    /** Negotiate supported API versions. */
    API_VERSIONS(18, "ApiVersions", 0, 3),
    /** Create topics. */
    CREATE_TOPICS(19, "CreateTopics", 0, 7),
    /** Delete topics. */
    DELETE_TOPICS(20, "DeleteTopics", 0, 6),
    /** Delete records before a given offset. */
    DELETE_RECORDS(21, "DeleteRecords", 0, 2),
    /** Initialize producer ID for idempotent/transactional production. */
    INIT_PRODUCER_ID(22, "InitProducerId", 0, 4),
    /** Find the log offset for a given leader epoch. */
    OFFSET_FOR_LEADER_EPOCH(23, "OffsetForLeaderEpoch", 0, 4),
    /** Add partitions to a transaction. */
    ADD_PARTITIONS_TO_TXN(24, "AddPartitionsToTxn", 0, 4),
    /** Add consumer group offsets partition to a transaction. */
    ADD_OFFSETS_TO_TXN(25, "AddOffsetsToTxn", 0, 3),
    /** Commit or abort a transaction. */
    END_TXN(26, "EndTxn", 0, 3),
    /** Write transaction markers to partition logs. */
    WRITE_TXN_MARKERS(27, "WriteTxnMarkers", 0, 1),
    /** Commit offsets within a transaction. */
    TXN_OFFSET_COMMIT(28, "TxnOffsetCommit", 0, 3),
    /** Describe broker or topic configurations. */
    DESCRIBE_CONFIGS(32, "DescribeConfigs", 0, 4),
    /** Alter broker or topic configurations. */
    ALTER_CONFIGS(33, "AlterConfigs", 0, 2),
    /** Perform SASL authentication exchange. */
    SASL_AUTHENTICATE(36, "SaslAuthenticate", 0, 2),
    /** Create additional partitions for existing topics. */
    CREATE_PARTITIONS(37, "CreatePartitions", 0, 3),
    /** Delete consumer groups. */
    DELETE_GROUPS(42, "DeleteGroups", 0, 2),
    /** Reassign partition replicas to different brokers. */
    ALTER_PARTITION_REASSIGNMENTS(45, "AlterPartitionReassignments", 0, 0),
    /** List ongoing partition reassignments. */
    LIST_PARTITION_REASSIGNMENTS(46, "ListPartitionReassignments", 0, 0),
    /** Delete committed offsets for a consumer group. */
    OFFSET_DELETE(47, "OffsetDelete", 0, 0);

    private final short key;
    private final String name;
    private final short minVersion;
    private final short maxVersion;

    ApiKey(int key, String name, int minVersion, int maxVersion) {
        this.key = (short) key;
        this.name = name;
        this.minVersion = (short) minVersion;
        this.maxVersion = (short) maxVersion;
    }

    /**
     * Returns the numeric API key.
     *
     * @return the API key
     */
    public short key() {
        return key;
    }

    /**
     * Returns the API name.
     *
     * @return the API name
     */
    public String apiName() {
        return name;
    }

    /**
     * Returns the minimum supported API version.
     *
     * @return the minimum version
     */
    public short minVersion() {
        return minVersion;
    }

    /**
     * Returns the maximum supported API version.
     *
     * @return the maximum version
     */
    public short maxVersion() {
        return maxVersion;
    }

    /**
     * Finds the API key enum for the given numeric key.
     *
     * @param key the numeric API key
     * @return the matching enum, or null if not found
     */
    public static ApiKey forKey(short key) {
        for (ApiKey ak : values()) {
            if (ak.key == key) return ak;
        }
        return null;
    }
}
