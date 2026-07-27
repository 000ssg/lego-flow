package ssg.legoflow.network.dns.protocol;

/**
 * Exception thrown when a DNS message cannot be parsed due to format errors.
 *
 * @since 1.0.0
 */
public class DnsFormatException extends RuntimeException {

    /**
     * Creates a format exception with a message.
     *
     * @param message the error message
     */
    public DnsFormatException(String message) {
        super(message);
    }

    /**
     * Creates a format exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public DnsFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
