package ssg.legoflow.network.syslog.protocol;

/**
 * Exception thrown when a syslog message cannot be parsed.
 *
 * @since 1.0.0
 */
public class SyslogParseException extends RuntimeException {

    /**
     * Creates a parse exception with a message.
     *
     * @param message the error message
     */
    public SyslogParseException(String message) {
        super(message);
    }

    /**
     * Creates a parse exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SyslogParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
