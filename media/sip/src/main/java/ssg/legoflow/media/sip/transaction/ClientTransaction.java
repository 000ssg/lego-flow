package ssg.legoflow.media.sip.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * SIP client transaction per RFC 3261 section 17.1.
 *
 * <p>Implements both INVITE and non-INVITE client transaction state machines.
 *
 * <h3>INVITE client transaction (section 17.1.1)</h3>
 * <pre>
 *   INITIAL -> CALLING (send INVITE)
 *   CALLING -> PROCEEDING (1xx received)
 *   CALLING -> COMPLETED (3xx-6xx received)
 *   CALLING -> TERMINATED (2xx received, dialog created)
 *   PROCEEDING -> PROCEEDING (1xx received)
 *   PROCEEDING -> COMPLETED (3xx-6xx received)
 *   PROCEEDING -> TERMINATED (2xx received)
 *   COMPLETED -> TERMINATED (Timer D fires)
 * </pre>
 *
 * <h3>Non-INVITE client transaction (section 17.1.2)</h3>
 * <pre>
 *   INITIAL -> TRYING (send request)
 *   TRYING -> PROCEEDING (1xx received)
 *   TRYING -> COMPLETED (final response received)
 *   PROCEEDING -> PROCEEDING (1xx received)
 *   PROCEEDING -> COMPLETED (final response received)
 *   COMPLETED -> TERMINATED (Timer K fires)
 * </pre>
 *
 * @since 1.0.0
 */
public final class ClientTransaction extends SipTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(ClientTransaction.class);

    private final CompletableFuture<SipResponse> responseFuture;
    private volatile SipResponse lastProvisionalResponse;
    private volatile SipResponse finalResponse;

    /**
     * Creates a client transaction.
     *
     * @param branchId        the branch ID
     * @param method          the request method
     * @param originalRequest the original request
     * @since 1.0.0
     */
    public ClientTransaction(String branchId, SipMethod method, SipRequest originalRequest) {
        super(branchId, method, originalRequest);
        this.responseFuture = new CompletableFuture<>();
    }

    /**
     * Starts the transaction by sending the request.
     *
     * <p>Transitions to CALLING (INVITE) or TRYING (non-INVITE).
     *
     * @since 1.0.0
     */
    public void start() {
        if (isInvite()) {
            transitionTo(TransactionState.CALLING);
        } else {
            transitionTo(TransactionState.TRYING);
        }
        LOG.debug("Client transaction started: {}", this);
    }

    /**
     * Processes a response received for this client transaction.
     *
     * @param response the received response
     * @since 1.0.0
     */
    @Override
    public void processResponse(SipResponse response) {
        Objects.requireNonNull(response, "response");
        int code = response.statusCode();

        if (isInvite()) {
            processInviteResponse(response, code);
        } else {
            processNonInviteResponse(response, code);
        }
    }

    private void processInviteResponse(SipResponse response, int code) {
        switch (state()) {
            case CALLING -> {
                if (code >= 100 && code < 200) {
                    lastProvisionalResponse = response;
                    transitionTo(TransactionState.PROCEEDING);
                    LOG.debug("INVITE client: CALLING -> PROCEEDING ({})", code);
                } else if (code >= 200 && code < 300) {
                    finalResponse = response;
                    transitionTo(TransactionState.TERMINATED);
                    responseFuture.complete(response);
                    LOG.debug("INVITE client: CALLING -> TERMINATED (2xx {})", code);
                } else if (code >= 300) {
                    finalResponse = response;
                    transitionTo(TransactionState.COMPLETED);
                    responseFuture.complete(response);
                    LOG.debug("INVITE client: CALLING -> COMPLETED ({})", code);
                }
            }
            case PROCEEDING -> {
                if (code >= 100 && code < 200) {
                    lastProvisionalResponse = response;
                    LOG.debug("INVITE client: PROCEEDING ({})", code);
                } else if (code >= 200 && code < 300) {
                    finalResponse = response;
                    transitionTo(TransactionState.TERMINATED);
                    responseFuture.complete(response);
                    LOG.debug("INVITE client: PROCEEDING -> TERMINATED (2xx {})", code);
                } else if (code >= 300) {
                    finalResponse = response;
                    transitionTo(TransactionState.COMPLETED);
                    responseFuture.complete(response);
                    LOG.debug("INVITE client: PROCEEDING -> COMPLETED ({})", code);
                }
            }
            case COMPLETED -> {
                // Absorb retransmissions in COMPLETED state
                LOG.debug("INVITE client: retransmission absorbed in COMPLETED ({})", code);
            }
            default -> LOG.debug("INVITE client: ignoring response in state {} ({})", state(), code);
        }
    }

    private void processNonInviteResponse(SipResponse response, int code) {
        switch (state()) {
            case TRYING -> {
                if (code >= 100 && code < 200) {
                    lastProvisionalResponse = response;
                    transitionTo(TransactionState.PROCEEDING);
                    LOG.debug("Non-INVITE client: TRYING -> PROCEEDING ({})", code);
                } else if (code >= 200) {
                    finalResponse = response;
                    transitionTo(TransactionState.COMPLETED);
                    responseFuture.complete(response);
                    LOG.debug("Non-INVITE client: TRYING -> COMPLETED ({})", code);
                }
            }
            case PROCEEDING -> {
                if (code >= 100 && code < 200) {
                    lastProvisionalResponse = response;
                    LOG.debug("Non-INVITE client: PROCEEDING ({})", code);
                } else if (code >= 200) {
                    finalResponse = response;
                    transitionTo(TransactionState.COMPLETED);
                    responseFuture.complete(response);
                    LOG.debug("Non-INVITE client: PROCEEDING -> COMPLETED ({})", code);
                }
            }
            case COMPLETED -> {
                LOG.debug("Non-INVITE client: retransmission absorbed in COMPLETED ({})", code);
            }
            default -> LOG.debug("Non-INVITE client: ignoring response in state {} ({})", state(), code);
        }
    }

    /**
     * Returns a future that completes when a final response is received.
     *
     * @return the response future
     * @since 1.0.0
     */
    public CompletableFuture<SipResponse> responseFuture() {
        return responseFuture;
    }

    /**
     * Returns the last provisional response, if any.
     *
     * @return the last provisional response, or null
     * @since 1.0.0
     */
    public SipResponse lastProvisionalResponse() {
        return lastProvisionalResponse;
    }

    /**
     * Returns the final response, if received.
     *
     * @return the final response, or null
     * @since 1.0.0
     */
    public SipResponse finalResponse() {
        return finalResponse;
    }

    @Override
    public void terminate() {
        super.terminate();
        if (!responseFuture.isDone()) {
            responseFuture.cancel(false);
        }
    }
}
