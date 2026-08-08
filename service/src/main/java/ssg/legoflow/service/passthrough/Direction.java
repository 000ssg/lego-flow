package ssg.legoflow.service.passthrough;

/**
 * Indicates the direction of data flow in a pass-through connection.
 *
 * @since 0.1.0
 */
public enum Direction {

    /** Data flowing from the local (client) socket to the remote (target) socket. */
    LOCAL_TO_REMOTE,

    /** Data flowing from the remote (target) socket to the local (client) socket. */
    REMOTE_TO_LOCAL
}
