package ssg.legoflow.network.ldap.codec;

/**
 * Exception thrown when encoding or decoding LDAP messages fails.
 *
 * @since 1.0.0
 */
public class LdapCodecException extends RuntimeException {

    /**
     * Creates a codec exception with a message.
     *
     * @param message the error message
     */
    public LdapCodecException(String message) {
        super(message);
    }

    /**
     * Creates a codec exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public LdapCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
