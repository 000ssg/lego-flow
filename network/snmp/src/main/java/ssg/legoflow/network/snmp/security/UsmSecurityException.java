package ssg.legoflow.network.snmp.security;

/**
 * Exception thrown when a USM security operation fails.
 *
 * @since 0.1.0
 */
public class UsmSecurityException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message the error message
     */
    public UsmSecurityException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public UsmSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
