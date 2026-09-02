package ssg.legoflow.service.cluster.coordination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
/**
 * Transaction for atomic compare-and-swap operations.
 *
 * <p>Per etcd v3 API, transactions are defined as:
 * <ul>
 *   <li>Compare: check key value against expected</li>
 *   <li>Success operations: executed if all comparisons pass</li>
 *   <li>Failure operations: executed if any comparison fails</li>
 * </ul>
 *
 * <p>All operations within a transaction are applied atomically.
 *
 * <p>Supports two usage patterns:
 * <pre>
 *   // Direct (one compare, one operation)
 *   boolean result = EtcdTransaction.create(store, key, expected)
 *       .thenPut(key, newValue).join();
 *
 *   // Builder-style (one compare, multiple operations)
 *   boolean result = EtcdTransaction.create(store, key, expected)
 *       .thenPut(key, newValue)
 *       .execute().join();
 * </pre>
 *
 * @since 0.2.0
 */
public final class EtcdTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdTransaction.class);

    private final EtcdKVStore store;
    private final String compareKey;
    private final byte[] compareValue;
    private final List<Operation> successOps = new CopyOnWriteArrayList<>();
    private final List<Operation> failureOps = new CopyOnWriteArrayList<>();

    /**
     * An operation to apply on success or failure of the compare.
     *
     * @param type  the operation type
     * @param key   the key to operate on
     * @param value the value (for put operations)
     * @param lease the lease (for put with lease)
     * @since 0.2.0
     */
    public record Operation(OperationType type, String key, byte[] value, EtcdLease lease) {}

    /**
     * Supported operation types.
     *
     * @since 0.2.0
     */
    public enum OperationType {
        /** A put operation. */
        PUT,
        /** A delete operation. */
        DELETE,
        /** A put with lease attachment. */
        PUT_WITH_LEASE
    }

    private EtcdTransaction(EtcdKVStore store, String compareKey, byte[] compareValue) {
        this.store = Objects.requireNonNull(store);
        this.compareKey = Objects.requireNonNull(compareKey);
        this.compareValue = compareValue; // may be null for existence check
    }

    /**
     * Creates a transaction comparing the given key with expected value.
     *
     * @param store        the KV store
     * @param compareKey   the key to compare
     * @param compareValue the expected value (null for existence check)
     * @return a new transaction
     * @since 0.2.0
     */
    public static EtcdTransaction create(EtcdKVStore store, String compareKey, byte[] compareValue) {
        return new EtcdTransaction(store, compareKey, compareValue);
    }

    /**
     * Adds a put operation to the success list.
     *
     * @param putKey   the key to put
     * @param putValue the value to put
     * @return this transaction for chaining
     * @since 0.2.0
     */
    public EtcdTransaction thenPut(String putKey, byte[] putValue) {
        Objects.requireNonNull(putKey);
        Objects.requireNonNull(putValue);
        successOps.add(new Operation(OperationType.PUT, putKey, putValue.clone(), null));
        return this;
    }

    /**
     * Adds a put-with-lease operation to the success list.
     *
     * @param putKey   the key to put
     * @param putValue the value to put
     * @param lease    the lease to attach
     * @return this transaction for chaining
     * @since 0.2.0
     */
    public EtcdTransaction thenPutWithLease(String putKey, byte[] putValue, EtcdLease lease) {
        Objects.requireNonNull(putKey);
        Objects.requireNonNull(putValue);
        Objects.requireNonNull(lease);
        successOps.add(new Operation(OperationType.PUT_WITH_LEASE, putKey, putValue.clone(), lease));
        return this;
    }

    /**
     * Adds a delete operation to the success list.
     *
     * @param deleteKey the key to delete
     * @return this transaction for chaining
     * @since 0.2.0
     */
    public EtcdTransaction thenDelete(String deleteKey) {
        Objects.requireNonNull(deleteKey);
        successOps.add(new Operation(OperationType.DELETE, deleteKey, null, null));
        return this;
    }

    /**
     * Executes the transaction: atomically checks the compare and applies
     * all success or failure operations.
     *
     * @return a future completed with true if the CAS succeeded
     * @since 0.2.0
     */
    public CompletableFuture<Boolean> execute() {
        byte[] current = store.store.get(compareKey);
        boolean matches = compareValue == null
                ? current == null
                : java.util.Arrays.equals(current, compareValue);

        if (matches) {
            LOG.debug("CAS succeeded for compareKey={}", compareKey);
            for (Operation op : successOps) {
                applyOp(op);
            }
        } else {
            LOG.debug("CAS failed for compareKey={} (expected={}, got={})",
                    compareKey, compareValue, current);
            for (Operation op : failureOps) {
                applyOp(op);
            }
        }

        return CompletableFuture.completedFuture(matches);
    }

    private void applyOp(Operation op) {
        switch (op.type()) {
            case PUT -> store.put(op.key(), op.value()).join();
            case PUT_WITH_LEASE -> store.put(op.key(), op.value(), op.lease()).join();
            case DELETE -> store.delete(op.key()).join();
        }
    }
}
