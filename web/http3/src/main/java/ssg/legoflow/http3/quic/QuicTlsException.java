package ssg.legoflow.http3.quic;

/**
 * Exception thrown when a QUIC TLS operation fails.
 *
 * @since 1.0.0
 */
public class QuicTlsException extends RuntimeException {

    /**
     * Creates a new TLS exception with a message.
     *
     * @param message the error message
     */
    public QuicTlsException(String message) {
        super(message);
    }

    /**
     * Creates a new TLS exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public QuicTlsException(String message, Throwable cause) {
        super(message, cause);
    }
}
