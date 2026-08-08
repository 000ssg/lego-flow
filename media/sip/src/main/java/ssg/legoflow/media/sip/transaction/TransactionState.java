package ssg.legoflow.media.sip.transaction;

/**
 * SIP transaction states per RFC 3261 section 17.
 *
 * <p>Covers both INVITE and non-INVITE client and server transaction states.
 *
 * @since 0.1.0
 */
public enum TransactionState {

    // Client transaction states (RFC 3261 section 17.1)
    /** Initial state before any request is sent. */
    INITIAL,

    /** INVITE client: request sent, waiting for provisional or final response. */
    CALLING,

    /** Non-INVITE client: request sent, waiting for response. */
    TRYING,

    /** INVITE client: provisional response received. */
    PROCEEDING,

    /** Final response received, ACK sent (INVITE client) or completed (non-INVITE). */
    COMPLETED,

    /** INVITE client: ACK sent, absorbing retransmissions. */
    CONFIRMED,

    /** Transaction terminated. */
    TERMINATED;

    /**
     * Returns true if this is a final state (COMPLETED, CONFIRMED, or TERMINATED).
     *
     * @return true if final
     * @since 0.1.0
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CONFIRMED || this == TERMINATED;
    }
}
