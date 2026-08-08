package ssg.legoflow.media.sip.transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ssg.legoflow.media.sip.protocol.SipMethod;
import ssg.legoflow.media.sip.protocol.SipRequest;
import ssg.legoflow.media.sip.protocol.SipResponse;

/**
 * SIP server transaction per RFC 3261 section 17.2.
 *
 * <p>Implements both INVITE and non-INVITE server transaction state machines.
 *
 * <h3>INVITE server transaction (section 17.2.1)</h3>
 * <pre>
 *   INITIAL -> PROCEEDING (request received, send 100)
 *   PROCEEDING -> PROCEEDING (send 1xx)
 *   PROCEEDING -> COMPLETED (send 3xx-6xx)
 *   PROCEEDING -> TERMINATED (send 2xx)
 *   COMPLETED -> CONFIRMED (ACK received)
 *   CONFIRMED -> TERMINATED (Timer I fires)
 * </pre>
 *
 * <h3>Non-INVITE server transaction (section 17.2.2)</h3>
 * <pre>
 *   INITIAL -> TRYING (request received)
 *   TRYING -> PROCEEDING (send 1xx)
 *   TRYING -> COMPLETED (send final response)
 *   PROCEEDING -> COMPLETED (send final response)
 *   COMPLETED -> TERMINATED (Timer J fires)
 * </pre>
 *
 * @since 0.1.0
 */
public final class ServerTransaction extends SipTransaction {

    private static final Logger LOG = LoggerFactory.getLogger(ServerTransaction.class);

    private volatile SipResponse lastResponse;

    /**
     * Creates a server transaction.
     *
     * @param branchId        the branch ID
     * @param method          the request method
     * @param originalRequest the original request
     * @since 0.1.0
     */
    public ServerTransaction(String branchId, SipMethod method, SipRequest originalRequest) {
        super(branchId, method, originalRequest);
    }

    /**
     * Starts the server transaction upon receiving a request.
     *
     * <p>Transitions to PROCEEDING (INVITE, with auto 100 Trying)
     * or TRYING (non-INVITE).
     *
     * @since 0.1.0
     */
    public void start() {
        if (isInvite()) {
            transitionTo(TransactionState.PROCEEDING);
            LOG.debug("INVITE server transaction started: {}", this);
        } else {
            transitionTo(TransactionState.TRYING);
            LOG.debug("Non-INVITE server transaction started: {}", this);
        }
    }

    /**
     * Sends a response through this server transaction.
     *
     * @param response the response to send
     * @since 0.1.0
     */
    public void sendResponse(SipResponse response) {
        int code = response.statusCode();
        lastResponse = response;

        if (isInvite()) {
            sendInviteResponse(response, code);
        } else {
            sendNonInviteResponse(response, code);
        }
    }

    private void sendInviteResponse(SipResponse response, int code) {
        switch (state()) {
            case PROCEEDING -> {
                if (code >= 100 && code < 200) {
                    LOG.debug("INVITE server: PROCEEDING, sending 1xx ({})", code);
                } else if (code >= 200 && code < 300) {
                    transitionTo(TransactionState.TERMINATED);
                    LOG.debug("INVITE server: PROCEEDING -> TERMINATED (2xx {})", code);
                } else if (code >= 300) {
                    transitionTo(TransactionState.COMPLETED);
                    LOG.debug("INVITE server: PROCEEDING -> COMPLETED ({})", code);
                }
            }
            case COMPLETED -> {
                // Retransmit final response
                LOG.debug("INVITE server: retransmitting in COMPLETED ({})", code);
            }
            default -> LOG.debug("INVITE server: ignoring send in state {} ({})", state(), code);
        }
    }

    private void sendNonInviteResponse(SipResponse response, int code) {
        switch (state()) {
            case TRYING -> {
                if (code >= 100 && code < 200) {
                    transitionTo(TransactionState.PROCEEDING);
                    LOG.debug("Non-INVITE server: TRYING -> PROCEEDING ({})", code);
                } else if (code >= 200) {
                    transitionTo(TransactionState.COMPLETED);
                    LOG.debug("Non-INVITE server: TRYING -> COMPLETED ({})", code);
                }
            }
            case PROCEEDING -> {
                if (code >= 200) {
                    transitionTo(TransactionState.COMPLETED);
                    LOG.debug("Non-INVITE server: PROCEEDING -> COMPLETED ({})", code);
                }
            }
            case COMPLETED -> {
                LOG.debug("Non-INVITE server: retransmitting in COMPLETED ({})", code);
            }
            default -> LOG.debug("Non-INVITE server: ignoring send in state {} ({})", state(), code);
        }
    }

    /**
     * Processes an ACK received for an INVITE server transaction.
     *
     * @since 0.1.0
     */
    public void processAck() {
        if (isInvite() && state() == TransactionState.COMPLETED) {
            transitionTo(TransactionState.CONFIRMED);
            LOG.debug("INVITE server: COMPLETED -> CONFIRMED (ACK received)");
        }
    }

    /**
     * Not applicable for server transactions. Response processing is done via
     * {@link #sendResponse(SipResponse)}.
     *
     * @param response ignored
     * @since 0.1.0
     */
    @Override
    public void processResponse(SipResponse response) {
        // Server transactions don't receive responses; they send them.
        // This method satisfies the abstract contract.
    }

    /**
     * Returns the last response sent through this transaction.
     *
     * @return the last response, or null
     * @since 0.1.0
     */
    public SipResponse lastResponse() {
        return lastResponse;
    }
}
