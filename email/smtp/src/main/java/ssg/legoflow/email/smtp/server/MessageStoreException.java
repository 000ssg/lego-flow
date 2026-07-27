package ssg.legoflow.email.smtp.server;

/**
 * Exception thrown when message storage fails.
 *
 * @since 1.0.0
 */
public class MessageStoreException extends Exception {

    /**
     * Creates a message store exception.
     *
     * @param message the error message
     */
    public MessageStoreException(String message) {
        super(message);
    }

    /**
     * Creates a message store exception with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public MessageStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
