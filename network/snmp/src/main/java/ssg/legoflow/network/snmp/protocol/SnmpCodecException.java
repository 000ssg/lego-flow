package ssg.legoflow.network.snmp.protocol;

/**
 * Exception thrown when SNMP BER encoding or decoding fails.
 *
 * @since 1.0.0
 */
public class SnmpCodecException extends RuntimeException {

    /**
     * Creates an exception with the given message.
     *
     * @param message the error message
     */
    public SnmpCodecException(String message) {
        super(message);
    }

    /**
     * Creates an exception with the given message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public SnmpCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
