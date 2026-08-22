package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Manages transactional producers: producer ID allocation, idempotency dedup,
 * and transaction lifecycle (begin, addPartitions, commit, abort).
 *
 * @since 0.1.0
 */
public final class TransactionManager {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionManager.class);

    /** Number of partitions for the {@code __consumer_offsets} internal topic. */
    private static final int CONSUMER_OFFSETS_PARTITIONS = 50;

    private final AtomicLong nextProducerId = new AtomicLong(1000);
    private final Map<String, ProducerState> transactionalProducers = new ConcurrentHashMap<>();
    private final Map<Long, ProducerState> producerStates = new ConcurrentHashMap<>();
    private volatile ConsumerGroupCoordinator groupCoordinator;

    /**
     * Transaction state.
     */
    public enum TxnState {
        EMPTY, ONGOING, PREPARE_COMMIT, PREPARE_ABORT, COMPLETE_COMMIT, COMPLETE_ABORT, DEAD
    }

    /**
     * State for a producer (idempotent or transactional).
     */
    static final class ProducerState {
        final long producerId;
        volatile short epoch;
        volatile String transactionalId;
        volatile TxnState txnState = TxnState.EMPTY;
        final Set<TopicPartition> txnPartitions = ConcurrentHashMap.newKeySet();
        // Sequence tracking for idempotent dedup: topic-partition -> last sequence number
        final Map<TopicPartition, Integer> lastSequence = new ConcurrentHashMap<>();
        // Transaction group ID for AddOffsetsToTxn
        volatile String txnGroupId;
        // Pending transactional offsets (stored until commit/abort)
        final Map<TopicPartition, Long> pendingTxnOffsets = new ConcurrentHashMap<>();

        ProducerState(long producerId, short epoch, String transactionalId) {
            this.producerId = producerId;
            this.epoch = epoch;
            this.transactionalId = transactionalId;
        }
    }

    /**
     * Result of InitProducerId.
     */
    public record InitResult(short errorCode, long producerId, short epoch) {
    }

    /**
     * Initializes a producer ID.
     *
     * @param transactionalId the transactional ID (null for idempotent-only)
     * @return the init result
     */
    public InitResult initProducerId(String transactionalId) {
        if (transactionalId != null) {
            // Transactional producer
            ProducerState existing = transactionalProducers.get(transactionalId);
            if (existing != null) {
                // Fence old producer
                existing.epoch++;
                existing.txnState = TxnState.EMPTY;
                existing.txnPartitions.clear();
                existing.pendingTxnOffsets.clear();
                existing.txnGroupId = null;
                LOG.debug("Fenced producer for txnId={}, new epoch={}", transactionalId, existing.epoch);
                return new InitResult(KafkaErrors.NONE.code(), existing.producerId, existing.epoch);
            }
            long pid = nextProducerId.getAndIncrement();
            ProducerState state = new ProducerState(pid, (short) 0, transactionalId);
            transactionalProducers.put(transactionalId, state);
            producerStates.put(pid, state);
            return new InitResult(KafkaErrors.NONE.code(), pid, (short) 0);
        } else {
            // Idempotent-only producer
            long pid = nextProducerId.getAndIncrement();
            ProducerState state = new ProducerState(pid, (short) 0, null);
            producerStates.put(pid, state);
            return new InitResult(KafkaErrors.NONE.code(), pid, (short) 0);
        }
    }

    /**
     * Checks a produce request for idempotency: detects duplicates and out-of-order sequences.
     *
     * @param producerId the producer ID
     * @param epoch      the producer epoch
     * @param tp         the topic-partition
     * @param baseSequence the base sequence number of the batch
     * @param recordCount  the number of records in the batch
     * @return error code (NONE if OK)
     */
    public short checkIdempotent(long producerId, short epoch, TopicPartition tp,
                                 int baseSequence, int recordCount) {
        if (producerId < 0) return KafkaErrors.NONE.code(); // non-idempotent

        ProducerState state = producerStates.get(producerId);
        if (state == null) return KafkaErrors.UNKNOWN_SERVER_ERROR.code();

        if (state.epoch != epoch) return KafkaErrors.INVALID_PRODUCER_EPOCH.code();

        Integer lastSeq = state.lastSequence.get(tp);
        if (lastSeq == null) {
            // First produce to this partition — accept if baseSequence is 0
            if (baseSequence != 0) {
                return KafkaErrors.OUT_OF_ORDER_SEQUENCE_NUMBER.code();
            }
        } else {
            int expectedSeq = lastSeq + 1;
            if (baseSequence == lastSeq - recordCount + 1) {
                // Duplicate of last batch
                return KafkaErrors.DUPLICATE_SEQUENCE_NUMBER.code();
            }
            if (baseSequence != expectedSeq) {
                return KafkaErrors.OUT_OF_ORDER_SEQUENCE_NUMBER.code();
            }
        }

        // Update last sequence
        state.lastSequence.put(tp, baseSequence + recordCount - 1);
        return KafkaErrors.NONE.code();
    }

    /**
     * Adds partitions to an ongoing transaction.
     *
     * @param transactionalId the transactional ID
     * @param producerId      the producer ID
     * @param epoch           the producer epoch
     * @param partitions      the partitions to add
     * @return error code per partition
     */
    public Map<TopicPartition, Short> addPartitionsToTxn(String transactionalId, long producerId,
                                                          short epoch, List<TopicPartition> partitions) {
        ProducerState state = transactionalProducers.get(transactionalId);
        Map<TopicPartition, Short> results = new LinkedHashMap<>();

        if (state == null) {
            for (TopicPartition tp : partitions) {
                results.put(tp, KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code());
            }
            return results;
        }

        if (state.producerId != producerId || state.epoch != epoch) {
            short error = state.epoch != epoch
                    ? KafkaErrors.INVALID_PRODUCER_EPOCH.code()
                    : KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
            for (TopicPartition tp : partitions) results.put(tp, error);
            return results;
        }

        state.txnState = TxnState.ONGOING;
        for (TopicPartition tp : partitions) {
            state.txnPartitions.add(tp);
            results.put(tp, KafkaErrors.NONE.code());
        }
        return results;
    }

    /**
     * Sets the consumer group coordinator used for flushing transactional offsets on commit.
     *
     * @param coordinator the group coordinator
     */
    public void setGroupCoordinator(ConsumerGroupCoordinator coordinator) {
        this.groupCoordinator = coordinator;
    }

    /**
     * Registers a consumer group's {@code __consumer_offsets} partition with an ongoing transaction.
     *
     * @param transactionalId the transactional ID
     * @param producerId      the producer ID
     * @param epoch           the producer epoch
     * @param groupId         the consumer group ID
     * @return the error code
     */
    public short addOffsetsToTxn(String transactionalId, long producerId, short epoch, String groupId) {
        ProducerState state = transactionalProducers.get(transactionalId);
        if (state == null) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.producerId != producerId) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.epoch != epoch) return KafkaErrors.INVALID_PRODUCER_EPOCH.code();

        if (state.txnState != TxnState.ONGOING && state.txnState != TxnState.EMPTY) {
            return KafkaErrors.INVALID_TXN_STATE.code();
        }

        state.txnState = TxnState.ONGOING;
        state.txnGroupId = groupId;

        // Register the __consumer_offsets partition for this group in the transaction
        int partitionIndex = Math.abs(groupId.hashCode()) % CONSUMER_OFFSETS_PARTITIONS;
        state.txnPartitions.add(new TopicPartition("__consumer_offsets", partitionIndex));

        LOG.debug("Added offsets for group {} to transaction {}, partition={}",
                groupId, transactionalId, partitionIndex);
        return KafkaErrors.NONE.code();
    }

    /**
     * Stores pending transactional offsets (not yet committed to the consumer group coordinator).
     *
     * @param transactionalId the transactional ID
     * @param producerId      the producer ID
     * @param epoch           the producer epoch
     * @param offsets         the offsets to store as pending
     * @return the error code
     */
    public short addPendingTxnOffsets(String transactionalId, long producerId, short epoch,
                                       Map<TopicPartition, Long> offsets) {
        ProducerState state = transactionalProducers.get(transactionalId);
        if (state == null) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.producerId != producerId) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.epoch != epoch) return KafkaErrors.INVALID_PRODUCER_EPOCH.code();

        if (state.txnState != TxnState.ONGOING && state.txnState != TxnState.EMPTY) {
            return KafkaErrors.INVALID_TXN_STATE.code();
        }

        state.pendingTxnOffsets.putAll(offsets);
        LOG.debug("Added pending txn offsets for transaction {}: {}", transactionalId, offsets);
        return KafkaErrors.NONE.code();
    }

    /**
     * Returns the pending transactional offsets for a transactional ID.
     *
     * @param transactionalId the transactional ID
     * @return the pending offsets, or an empty map if not found
     */
    public Map<TopicPartition, Long> getPendingTxnOffsets(String transactionalId) {
        ProducerState state = transactionalProducers.get(transactionalId);
        if (state == null) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(state.pendingTxnOffsets));
    }

    /**
     * Returns the registered group ID for a transactional ID.
     *
     * @param transactionalId the transactional ID
     * @return the group ID, or null if not set
     */
    public String getTxnGroupId(String transactionalId) {
        ProducerState state = transactionalProducers.get(transactionalId);
        return state != null ? state.txnGroupId : null;
    }

    /**
     * Ends a transaction (commit or abort).
     *
     * <p>If committing, flushes any pending transactional offsets to the consumer group coordinator.
     * If aborting, discards pending offsets.
     *
     * @param transactionalId the transactional ID
     * @param producerId      the producer ID
     * @param epoch           the producer epoch
     * @param commit          true to commit, false to abort
     * @return the error code
     */
    public short endTransaction(String transactionalId, long producerId, short epoch, boolean commit) {
        ProducerState state = transactionalProducers.get(transactionalId);
        if (state == null) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.producerId != producerId) return KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code();
        if (state.epoch != epoch) return KafkaErrors.INVALID_PRODUCER_EPOCH.code();

        if (state.txnState != TxnState.ONGOING && state.txnState != TxnState.EMPTY) {
            return KafkaErrors.INVALID_TXN_STATE.code();
        }

        if (commit) {
            state.txnState = TxnState.COMPLETE_COMMIT;
            // Flush pending offsets to the consumer group coordinator
            if (groupCoordinator != null && state.txnGroupId != null
                    && !state.pendingTxnOffsets.isEmpty()) {
                groupCoordinator.commitOffsets(state.txnGroupId, state.pendingTxnOffsets);
                LOG.debug("Flushed {} pending txn offsets for group {} on commit",
                        state.pendingTxnOffsets.size(), state.txnGroupId);
            }
            LOG.debug("Transaction committed: txnId={}, partitions={}", transactionalId, state.txnPartitions);
        } else {
            state.txnState = TxnState.COMPLETE_ABORT;
            LOG.debug("Transaction aborted: txnId={}, partitions={}", transactionalId, state.txnPartitions);
        }

        state.txnPartitions.clear();
        state.pendingTxnOffsets.clear();
        state.txnGroupId = null;
        state.txnState = TxnState.EMPTY;
        return KafkaErrors.NONE.code();
    }

    /**
     * Gets the transaction state for a transactional ID.
     *
     * @param transactionalId the transactional ID
     * @return the state, or null if not found
     */
    public TxnState getTransactionState(String transactionalId) {
        ProducerState state = transactionalProducers.get(transactionalId);
        return state != null ? state.txnState : null;
    }

    /**
     * Gets the producer state for a producer ID.
     *
     * @param producerId the producer ID
     * @return the state, or null if not found
     */
    ProducerState getProducerState(long producerId) {
        return producerStates.get(producerId);
    }
}
