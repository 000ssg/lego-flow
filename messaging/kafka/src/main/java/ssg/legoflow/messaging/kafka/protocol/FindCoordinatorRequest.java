package ssg.legoflow.messaging.kafka.protocol;

/**
 * FindCoordinator request (API key 10).
 *
 * @param key     the coordinator key (group ID or transactional ID)
 * @param keyType the key type (0=group, 1=transaction)
 * @since 0.1.0
 */
public record FindCoordinatorRequest(String key, byte keyType) {

    /** Key type for consumer group coordinator. */
    public static final byte KEY_TYPE_GROUP = 0;
    /** Key type for transaction coordinator. */
    public static final byte KEY_TYPE_TRANSACTION = 1;
}
