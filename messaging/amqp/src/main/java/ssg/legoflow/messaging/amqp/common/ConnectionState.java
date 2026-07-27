package ssg.legoflow.messaging.amqp.common;

/**
 * Connection lifecycle states as defined by the AMQP 1.0 specification.
 *
 * <p>Transitions follow the state machine:
 * {@code START -> HDR_SENT/HDR_RCVD -> HDR_EXCH -> OPEN_PIPE/OPEN_SENT/OPEN_RCVD -> OPENED
 * -> CLOSE_PIPE/CLOSE_SENT/CLOSE_RCVD -> END}
 *
 * @since 1.0.0
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
    FAILED
}
