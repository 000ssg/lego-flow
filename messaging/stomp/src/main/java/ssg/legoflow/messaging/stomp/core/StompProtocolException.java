package ssg.legoflow.messaging.stomp.core;

/**
 * Exception thrown for STOMP protocol violations such as malformed frames,
 * missing required headers, or invalid state transitions.
 *
 * @since 1.0.0
 */
public class StompProtocolException extends RuntimeException {

    /**
     * Creates a protocol exception with a message.
     *
     * @param message the error description
     */
    public StompProtocolException(String message) {
        super(message);
    }

    /**
     * Creates a protocol exception with a message and cause.
     *
     * @param message the error description
     * @param cause   the underlying cause
     */
    public StompProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
