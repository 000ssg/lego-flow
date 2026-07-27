package ssg.legoflow.network.common.ber;

/**
 * Exception thrown when BER decoding encounters malformed or unexpected data.
 *
 * @since 1.0.0
 */
public class BerDecodingException extends RuntimeException {

    /**
     * Creates a new BER decoding exception.
     *
     * @param message the detail message
     */
    public BerDecodingException(String message) {
        super(message);
    }

    /**
     * Creates a new BER decoding exception with a cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public BerDecodingException(String message, Throwable cause) {
        super(message, cause);
    }
}
