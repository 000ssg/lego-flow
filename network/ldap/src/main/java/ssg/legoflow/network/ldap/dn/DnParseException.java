package ssg.legoflow.network.ldap.dn;

/**
 * Exception thrown when parsing a Distinguished Name string fails.
 *
 * @since 0.1.0
 */
public class DnParseException extends RuntimeException {

    /**
     * Creates a DN parse exception with a message.
     *
     * @param message the error message
     */
    public DnParseException(String message) {
        super(message);
    }

    /**
     * Creates a DN parse exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public DnParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
