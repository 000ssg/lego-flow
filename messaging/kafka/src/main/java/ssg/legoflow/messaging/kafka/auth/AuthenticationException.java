package ssg.legoflow.messaging.kafka.auth;

/**
 * Exception thrown when SASL authentication fails.
 *
 * @since 0.1.0
 */
public class AuthenticationException extends Exception {

    /**
     * Creates a new authentication exception.
     *
     * @param message the error message
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * Creates a new authentication exception with a cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
