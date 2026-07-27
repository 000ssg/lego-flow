package ssg.legoflow.network.ldap.filter;

/**
 * Exception thrown when parsing an LDAP filter string fails.
 *
 * @since 1.0.0
 */
public class FilterParseException extends RuntimeException {

    /**
     * Creates a filter parse exception with a message.
     *
     * @param message the error message
     */
    public FilterParseException(String message) {
        super(message);
    }

    /**
     * Creates a filter parse exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public FilterParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
