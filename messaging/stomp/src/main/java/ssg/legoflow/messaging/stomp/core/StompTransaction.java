package ssg.legoflow.messaging.stomp.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Transaction buffer for STOMP transactions.
 *
 * <p>STOMP transactions (BEGIN/COMMIT/ABORT) buffer SEND, ACK, and NACK frames
 * until the transaction is committed or aborted. On COMMIT, all buffered frames
 * are applied atomically. On ABORT, all buffered frames are discarded.
 *
 * @since 1.0.0
 */
public class StompTransaction {

    private final String transactionId;
    private final List<StompFrame> bufferedFrames = new ArrayList<>();
    private volatile boolean committed;
    private volatile boolean aborted;

    /**
     * Creates a new transaction.
     *
     * @param transactionId the transaction identifier
     */
    public StompTransaction(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Returns the transaction identifier.
     *
     * @return the transaction ID
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Buffers a frame in this transaction.
     *
     * <p>Only SEND, ACK, and NACK frames should be buffered per the STOMP spec.
     *
     * @param frame the frame to buffer
     * @throws IllegalStateException if the transaction is already committed or aborted
     */
    public void buffer(StompFrame frame) {
        if (committed || aborted) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " is already " + (committed ? "committed" : "aborted"));
        }
        bufferedFrames.add(frame);
    }

    /**
     * Returns the buffered frames and marks the transaction as committed.
     *
     * @return the list of buffered frames
     * @throws IllegalStateException if the transaction is already committed or aborted
     */
    public List<StompFrame> commit() {
        if (committed || aborted) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " is already " + (committed ? "committed" : "aborted"));
        }
        committed = true;
        return Collections.unmodifiableList(bufferedFrames);
    }

    /**
     * Discards all buffered frames and marks the transaction as aborted.
     *
     * @throws IllegalStateException if the transaction is already committed or aborted
     */
    public void abort() {
        if (committed || aborted) {
            throw new IllegalStateException(
                    "Transaction " + transactionId + " is already " + (committed ? "committed" : "aborted"));
        }
        aborted = true;
        bufferedFrames.clear();
    }

    /**
     * Returns whether this transaction has been committed.
     *
     * @return {@code true} if committed
     */
    public boolean isCommitted() {
        return committed;
    }

    /**
     * Returns whether this transaction has been aborted.
     *
     * @return {@code true} if aborted
     */
    public boolean isAborted() {
        return aborted;
    }

    /**
     * Returns whether this transaction is still active (not committed or aborted).
     *
     * @return {@code true} if active
     */
    public boolean isActive() {
        return !committed && !aborted;
    }

    /**
     * Returns the number of buffered frames.
     *
     * @return the buffer size
     */
    public int size() {
        return bufferedFrames.size();
    }

    /**
     * Returns an unmodifiable view of the buffered frames.
     *
     * @return the buffered frames
     */
    public List<StompFrame> getBufferedFrames() {
        return Collections.unmodifiableList(bufferedFrames);
    }

    @Override
    public String toString() {
        return "StompTransaction[id=" + transactionId
                + ", frames=" + bufferedFrames.size()
                + ", state=" + (committed ? "committed" : aborted ? "aborted" : "active")
                + "]";
    }
}
