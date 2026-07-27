package ssg.legoflow.media.sip.transaction;

import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;

import java.util.Objects;

/**
 * Base class for SIP transactions per RFC 3261 section 17.
 *
 * <p>A transaction is identified by its branch parameter in the Via header.
 * There are four transaction types: INVITE client, INVITE server,
 * non-INVITE client, and non-INVITE server.
 *
 * @since 1.0.0
 */
public abstract sealed class SipTransaction
        permits ClientTransaction, ServerTransaction {

    private final String branchId;
    private final SipMethod method;
    private final SipRequest originalRequest;
    private volatile TransactionState state;

    /**
     * Creates a new transaction.
     *
     * @param branchId        the branch ID (transaction identifier)
     * @param method          the request method
     * @param originalRequest the original request
     * @since 1.0.0
     */
    protected SipTransaction(String branchId, SipMethod method, SipRequest originalRequest) {
        this.branchId = Objects.requireNonNull(branchId, "branchId");
        this.method = Objects.requireNonNull(method, "method");
        this.originalRequest = Objects.requireNonNull(originalRequest, "originalRequest");
        this.state = TransactionState.INITIAL;
    }

    /**
     * Returns the branch ID.
     *
     * @return the branch ID
     * @since 1.0.0
     */
    public String branchId() {
        return branchId;
    }

    /**
     * Returns the request method.
     *
     * @return the method
     * @since 1.0.0
     */
    public SipMethod method() {
        return method;
    }

    /**
     * Returns the original request.
     *
     * @return the request
     * @since 1.0.0
     */
    public SipRequest originalRequest() {
        return originalRequest;
    }

    /**
     * Returns the current transaction state.
     *
     * @return the state
     * @since 1.0.0
     */
    public TransactionState state() {
        return state;
    }

    /**
     * Returns true if this is an INVITE transaction.
     *
     * @return true for INVITE transactions
     * @since 1.0.0
     */
    public boolean isInvite() {
        return method == SipMethod.INVITE;
    }

    /**
     * Returns true if the transaction is terminated.
     *
     * @return true if terminated
     * @since 1.0.0
     */
    public boolean isTerminated() {
        return state == TransactionState.TERMINATED;
    }

    /**
     * Transitions to a new state.
     *
     * @param newState the new state
     * @since 1.0.0
     */
    protected void transitionTo(TransactionState newState) {
        this.state = newState;
    }

    /**
     * Processes a response for this transaction.
     *
     * @param response the received response
     * @since 1.0.0
     */
    public abstract void processResponse(SipResponse response);

    /**
     * Terminates this transaction.
     *
     * @since 1.0.0
     */
    public void terminate() {
        transitionTo(TransactionState.TERMINATED);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[branch=" + branchId
                + ", method=" + method + ", state=" + state + "]";
    }
}
