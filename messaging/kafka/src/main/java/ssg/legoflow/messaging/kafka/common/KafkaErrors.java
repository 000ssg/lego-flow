package ssg.legoflow.messaging.kafka.common;

/**
 * Kafka protocol error codes as defined in the Apache Kafka specification.
 *
 * @since 0.1.0
 */
public enum KafkaErrors {

    /** No error — operation succeeded. */
    NONE(0, "No error"),
    /** An unexpected server error. */
    UNKNOWN_SERVER_ERROR(-1, "Unknown server error"),
    /** The requested offset is not within the range of offsets maintained by the server. */
    OFFSET_OUT_OF_RANGE(1, "Offset out of range"),
    /** Message contents does not match its CRC. */
    CORRUPT_MESSAGE(2, "Corrupt message"),
    /** This server does not host this topic-partition. */
    UNKNOWN_TOPIC_OR_PARTITION(3, "Unknown topic or partition"),
    /** The message has a negative size. */
    INVALID_FETCH_SIZE(4, "Invalid fetch size"),
    /** This server is not the leader for that topic-partition. */
    LEADER_NOT_AVAILABLE(5, "Leader not available"),
    /** Not the leader. */
    NOT_LEADER_OR_FOLLOWER(6, "Not leader or follower"),
    /** Request timed out. */
    REQUEST_TIMED_OUT(7, "Request timed out"),
    /** The broker is not available. */
    BROKER_NOT_AVAILABLE(8, "Broker not available"),
    /** The replica is not available. */
    REPLICA_NOT_AVAILABLE(9, "Replica not available"),
    /** The request included a message larger than the max message size the server will accept. */
    MESSAGE_TOO_LARGE(10, "Message too large"),
    /** The controller moved to another broker. */
    STALE_CONTROLLER_EPOCH(11, "Stale controller epoch"),
    /** The metadata field of the offset request was too large. */
    OFFSET_METADATA_TOO_LARGE(12, "Offset metadata too large"),
    /** The server disconnected before a response was received. */
    NETWORK_EXCEPTION(13, "Network exception"),
    /** The coordinator is loading and hence can't process requests. */
    COORDINATOR_LOAD_IN_PROGRESS(14, "Coordinator load in progress"),
    /** The coordinator is not available. */
    COORDINATOR_NOT_AVAILABLE(15, "Coordinator not available"),
    /** Not coordinator for this group. */
    NOT_COORDINATOR(16, "Not coordinator"),
    /** Invalid topic. */
    INVALID_TOPIC_EXCEPTION(17, "Invalid topic"),
    /** Record batch too large. */
    RECORD_LIST_TOO_LARGE(18, "Record list too large"),
    /** Not enough replicas to satisfy the request. */
    NOT_ENOUGH_REPLICAS(19, "Not enough replicas"),
    /** Not enough replicas have acknowledged the message. */
    NOT_ENOUGH_REPLICAS_AFTER_APPEND(20, "Not enough replicas after append"),
    /** Invalid number of required acks. */
    INVALID_REQUIRED_ACKS(21, "Invalid required acks"),
    /** Specified group generation id is not valid. */
    ILLEGAL_GENERATION(22, "Illegal generation"),
    /** The group member's supported protocols are incompatible with those of existing members. */
    INCONSISTENT_GROUP_PROTOCOL(23, "Inconsistent group protocol"),
    /** The configured groupId is invalid. */
    INVALID_GROUP_ID(24, "Invalid group ID"),
    /** This is not the correct group. */
    UNKNOWN_MEMBER_ID(25, "Unknown member ID"),
    /** The session timeout is not within the range allowed by the broker. */
    INVALID_SESSION_TIMEOUT(26, "Invalid session timeout"),
    /** The group is rebalancing. */
    REBALANCE_IN_PROGRESS(27, "Rebalance in progress"),
    /** The committing offset data size is not valid. */
    INVALID_COMMIT_OFFSET_SIZE(28, "Invalid commit offset size"),
    /** Topic authorization failed. */
    TOPIC_AUTHORIZATION_FAILED(29, "Topic authorization failed"),
    /** Group authorization failed. */
    GROUP_AUTHORIZATION_FAILED(30, "Group authorization failed"),
    /** Cluster authorization failed. */
    CLUSTER_AUTHORIZATION_FAILED(31, "Cluster authorization failed"),
    /** The timestamp of the message is out of acceptable range. */
    INVALID_TIMESTAMP(32, "Invalid timestamp"),
    /** The broker does not support the requested SASL mechanism. */
    UNSUPPORTED_SASL_MECHANISM(33, "Unsupported SASL mechanism"),
    /** Request is not valid given the current SASL state. */
    ILLEGAL_SASL_STATE(34, "Illegal SASL state"),
    /** The version of API is not supported. */
    UNSUPPORTED_VERSION(35, "Unsupported version"),
    /** Topic with this name already exists. */
    TOPIC_ALREADY_EXISTS(36, "Topic already exists"),
    /** Number of partitions is below 1. */
    INVALID_PARTITIONS(37, "Invalid partitions"),
    /** Replication factor is below 1 or larger than the number of available brokers. */
    INVALID_REPLICATION_FACTOR(38, "Invalid replication factor"),
    /** Replica assignment is invalid. */
    INVALID_REPLICA_ASSIGNMENT(39, "Invalid replica assignment"),
    /** Configuration is invalid. */
    INVALID_CONFIG(40, "Invalid config"),
    /** This is not the correct controller for this cluster. */
    NOT_CONTROLLER(41, "Not controller"),
    /** Invalid request. */
    INVALID_REQUEST(42, "Invalid request"),
    /** The message format version on the broker does not support the request. */
    UNSUPPORTED_FOR_MESSAGE_FORMAT(43, "Unsupported for message format"),
    /** Request parameters do not satisfy the configured policy. */
    POLICY_VIOLATION(44, "Policy violation"),
    /** The broker received an out of order sequence number. */
    OUT_OF_ORDER_SEQUENCE_NUMBER(45, "Out of order sequence number"),
    /** The broker received a duplicate sequence number. */
    DUPLICATE_SEQUENCE_NUMBER(46, "Duplicate sequence number"),
    /** Producer attempted an operation with an old epoch. */
    INVALID_PRODUCER_EPOCH(47, "Invalid producer epoch"),
    /** The producer attempted a transactional operation in an invalid state. */
    INVALID_TXN_STATE(48, "Invalid transaction state"),
    /** The producer attempted to use a producer id which is not currently assigned to its transactional id. */
    INVALID_PRODUCER_ID_MAPPING(49, "Invalid producer ID mapping"),
    /** The transaction timeout is larger than the maximum value allowed by the broker. */
    INVALID_TRANSACTION_TIMEOUT(50, "Invalid transaction timeout"),
    /** The producer attempted a concurrent operation on the same transactional id. */
    CONCURRENT_TRANSACTIONS(51, "Concurrent transactions"),
    /** The transaction coordinator fenced the producer. */
    TRANSACTIONAL_ID_AUTHORIZATION_FAILED(53, "Transactional ID authorization failed"),
    /** The group is not empty and cannot be deleted. */
    NON_EMPTY_GROUP(68, "Non-empty group"),
    /** The group ID was not found. */
    GROUP_ID_NOT_FOUND(69, "Group ID not found"),
    /** The producer is fenced — another producer with same producer id has started. */
    PRODUCER_FENCED(90, "Producer fenced");

    private final short code;
    private final String message;

    KafkaErrors(int code, String message) {
        this.code = (short) code;
        this.message = message;
    }

    /**
     * Returns the numeric error code.
     *
     * @return the error code
     */
    public short code() {
        return code;
    }

    /**
     * Returns the error message.
     *
     * @return the error message
     */
    public String message() {
        return message;
    }

    /**
     * Finds the error enum for the given code.
     *
     * @param code the error code
     * @return the matching enum, or {@link #UNKNOWN_SERVER_ERROR} if not found
     */
    public static KafkaErrors forCode(short code) {
        for (KafkaErrors e : values()) {
            if (e.code == code) return e;
        }
        return UNKNOWN_SERVER_ERROR;
    }
}
