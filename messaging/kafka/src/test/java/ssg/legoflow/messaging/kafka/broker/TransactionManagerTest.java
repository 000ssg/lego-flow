package ssg.legoflow.messaging.kafka.broker;

import ssg.legoflow.messaging.kafka.common.KafkaErrors;
import ssg.legoflow.messaging.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
class TransactionManagerTest {

    private TransactionManager txnManager;

    @BeforeEach
    void setUp() {
        txnManager = new TransactionManager();
    }

    // --- InitProducerId ---

    @Test
    void testInitIdempotentProducer() {
        var result = txnManager.initProducerId(null);
        assertThat(result.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.producerId()).isGreaterThan(0);
        assertThat(result.epoch()).isZero();
    }

    @Test
    void testInitTransactionalProducer() {
        var result = txnManager.initProducerId("txn-1");
        assertThat(result.errorCode()).isEqualTo(KafkaErrors.NONE.code());
        assertThat(result.producerId()).isGreaterThan(0);
        assertThat(result.epoch()).isZero();
    }

    @Test
    void testInitProducerIdUnique() {
        var r1 = txnManager.initProducerId(null);
        var r2 = txnManager.initProducerId(null);
        assertThat(r1.producerId()).isNotEqualTo(r2.producerId());
    }

    @Test
    void testFenceOldProducer() {
        var r1 = txnManager.initProducerId("txn-1");
        var r2 = txnManager.initProducerId("txn-1"); // Re-init same txn ID

        assertThat(r2.producerId()).isEqualTo(r1.producerId());
        assertThat(r2.epoch()).isEqualTo((short) (r1.epoch() + 1));
    }

    // --- Idempotent dedup ---

    @Test
    void testIdempotentFirstProduce() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        short error = txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 0, 1);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testIdempotentSequentialProduce() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        assertThat(txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 0, 3))
                .isEqualTo(KafkaErrors.NONE.code());
        assertThat(txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 3, 2))
                .isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testIdempotentDuplicateDetection() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 0, 3);
        short error = txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 0, 3);
        assertThat(error).isEqualTo(KafkaErrors.DUPLICATE_SEQUENCE_NUMBER.code());
    }

    @Test
    void testIdempotentOutOfOrder() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 0, 1);
        short error = txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 5, 1);
        assertThat(error).isEqualTo(KafkaErrors.OUT_OF_ORDER_SEQUENCE_NUMBER.code());
    }

    @Test
    void testIdempotentWrongEpoch() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        short error = txnManager.checkIdempotent(init.producerId(), (short) (init.epoch() + 1), tp, 0, 1);
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_EPOCH.code());
    }

    @Test
    void testIdempotentNonIdempotentProducer() {
        var tp = new TopicPartition("test", 0);
        short error = txnManager.checkIdempotent(-1, (short) -1, tp, 0, 1);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testIdempotentFirstSequenceMustBeZero() {
        var init = txnManager.initProducerId(null);
        var tp = new TopicPartition("test", 0);

        short error = txnManager.checkIdempotent(init.producerId(), init.epoch(), tp, 5, 1);
        assertThat(error).isEqualTo(KafkaErrors.OUT_OF_ORDER_SEQUENCE_NUMBER.code());
    }

    @Test
    void testIdempotentMultiplePartitions() {
        var init = txnManager.initProducerId(null);
        var tp0 = new TopicPartition("test", 0);
        var tp1 = new TopicPartition("test", 1);

        // Each partition tracks sequence independently
        assertThat(txnManager.checkIdempotent(init.producerId(), init.epoch(), tp0, 0, 1))
                .isEqualTo(KafkaErrors.NONE.code());
        assertThat(txnManager.checkIdempotent(init.producerId(), init.epoch(), tp1, 0, 1))
                .isEqualTo(KafkaErrors.NONE.code());
    }

    // --- Transactions ---

    @Test
    void testAddPartitionsToTxn() {
        var init = txnManager.initProducerId("txn-1");
        var partitions = List.of(
                new TopicPartition("topic", 0),
                new TopicPartition("topic", 1));

        var results = txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(), partitions);
        for (var entry : results.entrySet()) {
            assertThat(entry.getValue()).isEqualTo(KafkaErrors.NONE.code());
        }
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.ONGOING);
    }

    @Test
    void testAddPartitionsToTxnUnknown() {
        var results = txnManager.addPartitionsToTxn("unknown", 999, (short) 0,
                List.of(new TopicPartition("t", 0)));
        assertThat(results.values()).allMatch(v -> v != KafkaErrors.NONE.code());
    }

    @Test
    void testAddPartitionsToTxnWrongEpoch() {
        var init = txnManager.initProducerId("txn-1");
        var results = txnManager.addPartitionsToTxn("txn-1", init.producerId(),
                (short) (init.epoch() + 1), List.of(new TopicPartition("t", 0)));
        assertThat(results.values()).allMatch(v -> v == KafkaErrors.INVALID_PRODUCER_EPOCH.code());
    }

    @Test
    void testCommitTransaction() {
        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));

        short error = txnManager.endTransaction("txn-1", init.producerId(), init.epoch(), true);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.EMPTY);
    }

    @Test
    void testAbortTransaction() {
        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));

        short error = txnManager.endTransaction("txn-1", init.producerId(), init.epoch(), false);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
    }

    @Test
    void testEndTransactionUnknown() {
        short error = txnManager.endTransaction("unknown", 999, (short) 0, true);
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code());
    }

    @Test
    void testEndTransactionWrongEpoch() {
        var init = txnManager.initProducerId("txn-1");
        short error = txnManager.endTransaction("txn-1", init.producerId(),
                (short) (init.epoch() + 1), true);
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_EPOCH.code());
    }

    @Test
    void testTransactionStateLifecycle() {
        assertThat(txnManager.getTransactionState("txn-1")).isNull();

        var init = txnManager.initProducerId("txn-1");
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.EMPTY);

        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.ONGOING);

        txnManager.endTransaction("txn-1", init.producerId(), init.epoch(), true);
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.EMPTY);
    }

    @Test
    void testFencingResetsTransaction() {
        var init1 = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init1.producerId(), init1.epoch(),
                List.of(new TopicPartition("t", 0)));

        // Fence by re-initializing
        var init2 = txnManager.initProducerId("txn-1");
        assertThat(init2.epoch()).isEqualTo((short) (init1.epoch() + 1));
        assertThat(txnManager.getTransactionState("txn-1"))
                .isEqualTo(TransactionManager.TxnState.EMPTY);
    }

    // --- AddOffsetsToTxn ---

    @Test
    void testAddOffsetsToTxn() {
        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));

        short error = txnManager.addOffsetsToTxn("txn-1", init.producerId(), init.epoch(), "my-group");
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());
        assertThat(txnManager.getTxnGroupId("txn-1")).isEqualTo("my-group");
    }

    @Test
    void testAddOffsetsToTxnUnknown() {
        short error = txnManager.addOffsetsToTxn("unknown", 999, (short) 0, "group");
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code());
    }

    @Test
    void testAddOffsetsToTxnWrongEpoch() {
        var init = txnManager.initProducerId("txn-1");
        short error = txnManager.addOffsetsToTxn("txn-1", init.producerId(),
                (short) (init.epoch() + 1), "group");
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_EPOCH.code());
    }

    // --- Pending Txn Offsets ---

    @Test
    void testAddPendingTxnOffsets() {
        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));

        var offsets = Map.of(new TopicPartition("t", 0), 42L, new TopicPartition("t", 1), 100L);
        short error = txnManager.addPendingTxnOffsets("txn-1", init.producerId(), init.epoch(), offsets);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());

        var pending = txnManager.getPendingTxnOffsets("txn-1");
        assertThat(pending).containsEntry(new TopicPartition("t", 0), 42L);
        assertThat(pending).containsEntry(new TopicPartition("t", 1), 100L);
    }

    @Test
    void testAddPendingTxnOffsetsUnknown() {
        short error = txnManager.addPendingTxnOffsets("unknown", 999, (short) 0, Map.of());
        assertThat(error).isEqualTo(KafkaErrors.INVALID_PRODUCER_ID_MAPPING.code());
    }

    @Test
    void testGetPendingTxnOffsetsUnknown() {
        var pending = txnManager.getPendingTxnOffsets("unknown");
        assertThat(pending).isEmpty();
    }

    @Test
    void testGetTxnGroupIdUnknown() {
        assertThat(txnManager.getTxnGroupId("unknown")).isNull();
    }

    // --- EndTransaction with offset flush ---

    @Test
    void testCommitTransactionFlushesOffsets() {
        var coordinator = new ConsumerGroupCoordinator();
        txnManager.setGroupCoordinator(coordinator);

        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));
        txnManager.addOffsetsToTxn("txn-1", init.producerId(), init.epoch(), "my-group");

        var offsets = Map.of(new TopicPartition("t", 0), 42L);
        txnManager.addPendingTxnOffsets("txn-1", init.producerId(), init.epoch(), offsets);

        short error = txnManager.endTransaction("txn-1", init.producerId(), init.epoch(), true);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());

        // Verify offsets were committed to the group coordinator
        var committed = coordinator.fetchOffsets("my-group",
                List.of(new TopicPartition("t", 0)));
        assertThat(committed).containsEntry(new TopicPartition("t", 0), 42L);

        // Verify pending offsets are cleared
        assertThat(txnManager.getPendingTxnOffsets("txn-1")).isEmpty();
        assertThat(txnManager.getTxnGroupId("txn-1")).isNull();
    }

    @Test
    void testAbortTransactionDiscardsOffsets() {
        var coordinator = new ConsumerGroupCoordinator();
        txnManager.setGroupCoordinator(coordinator);

        var init = txnManager.initProducerId("txn-1");
        txnManager.addPartitionsToTxn("txn-1", init.producerId(), init.epoch(),
                List.of(new TopicPartition("t", 0)));
        txnManager.addOffsetsToTxn("txn-1", init.producerId(), init.epoch(), "my-group");

        var offsets = Map.of(new TopicPartition("t", 0), 42L);
        txnManager.addPendingTxnOffsets("txn-1", init.producerId(), init.epoch(), offsets);

        short error = txnManager.endTransaction("txn-1", init.producerId(), init.epoch(), false);
        assertThat(error).isEqualTo(KafkaErrors.NONE.code());

        // Verify offsets were NOT committed to the group coordinator
        var committed = coordinator.fetchOffsets("my-group",
                List.of(new TopicPartition("t", 0)));
        assertThat(committed).containsEntry(new TopicPartition("t", 0), -1L);

        // Verify pending offsets are cleared
        assertThat(txnManager.getPendingTxnOffsets("txn-1")).isEmpty();
    }

    @Test
    void testMultipleTransactions() {
        var init1 = txnManager.initProducerId("txn-a");
        var init2 = txnManager.initProducerId("txn-b");

        txnManager.addPartitionsToTxn("txn-a", init1.producerId(), init1.epoch(),
                List.of(new TopicPartition("t", 0)));
        txnManager.addPartitionsToTxn("txn-b", init2.producerId(), init2.epoch(),
                List.of(new TopicPartition("t", 1)));

        txnManager.endTransaction("txn-a", init1.producerId(), init1.epoch(), true);
        txnManager.endTransaction("txn-b", init2.producerId(), init2.epoch(), false);

        assertThat(txnManager.getTransactionState("txn-a")).isEqualTo(TransactionManager.TxnState.EMPTY);
        assertThat(txnManager.getTransactionState("txn-b")).isEqualTo(TransactionManager.TxnState.EMPTY);
    }
}
