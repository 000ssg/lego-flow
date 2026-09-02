package ssg.legoflow.messaging.amqp.common;

/**
 * Connection lifecycle states as defined by the AMQP 1.0 specification.
 *
 * <p>Transitions follow the state machine:
 * {@code START -> HDR_SENT/HDR_RCVD -> HDR_EXCH -> OPEN_PIPE/OPEN_SENT/OPEN_RCVD -> OPENED
 * -> CLOSE_PIPE/CLOSE_SENT/CLOSE_RCVD -> END}
 *
 * @since 0.1.0
 */
public enum ConnectionState {

    /** Initial state before any data is exchanged. */
    START,

    /** Protocol header has been sent. */
    HDR_SENT,

    /** Protocol header has been received. */
    HDR_RCVD,

    /** Protocol headers have been exchanged in both directions. */
    HDR_EXCH,

    /** Open frame sent before header exchange complete (pipelining). */
    OPEN_PIPE,

    /** Open frame sent, waiting for peer open. */
    OPEN_SENT,

    /** Peer open received, own open not yet sent. */
    OPEN_RCVD,

    /** Connection fully open — both peers have exchanged open frames. */
    OPENED,

    /** Close frame sent before open exchange complete (pipelining). */
    CLOSE_PIPE,

    /** Close frame sent, waiting for peer close. */
    CLOSE_SENT,

    /** Peer close received, own close not yet sent. */
    CLOSE_RCVD,

    /** Connection fully closed. */
    END,

    /** Connection failed due to an error. */
    FAILED;

    /**
     * Checks if transitioning from this state to the given state is valid.
     *
     * <p>Valid transitions follow the AMQP 1.0 connection lifecycle:
     * {@code START -> HDR_SENT/HDR_RCVD -> HDR_EXCH -> OPEN_SENT/OPEN_RCVD -> OPENED
     * -> CLOSE_SENT/CLOSE_RCVD -> END}
     *
     * @param target the target state
     * @return true if the transition is valid
     */
    public boolean isValidTransition(ConnectionState target) {
        if (this == target) return true;
        switch (this) {
            case START:
                return target == HDR_SENT || target == HDR_RCVD;
            case HDR_SENT:
                return target == HDR_RCVD || target == HDR_EXCH;
            case HDR_RCVD:
                return target == HDR_SENT || target == HDR_EXCH;
            case HDR_EXCH:
                return target == OPEN_PIPE || target == OPEN_SENT || target == OPEN_RCVD;
            case OPEN_PIPE:
                return target == HDR_EXCH || target == OPEN_SENT || target == OPEN_RCVD || target == OPENED;
            case OPEN_SENT:
                return target == OPEN_RCVD || target == OPENED;
            case OPEN_RCVD:
                return target == OPENED;
            case OPENED:
                return target == CLOSE_PIPE || target == CLOSE_SENT || target == CLOSE_RCVD;
            case CLOSE_PIPE:
                return target == HDR_EXCH || target == CLOSE_SENT || target == CLOSE_RCVD || target == END;
            case CLOSE_SENT:
                return target == CLOSE_RCVD || target == END;
            case CLOSE_RCVD:
                return target == END;
            case END:
            case FAILED:
                return false;
            default:
                return false;
        }
    }
}
