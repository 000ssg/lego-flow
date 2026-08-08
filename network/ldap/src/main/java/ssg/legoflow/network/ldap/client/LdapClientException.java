package ssg.legoflow.network.ldap.client;

/**
 * Exception thrown by the LDAP client for protocol-level errors.
 *
 * @since 0.1.0
 */
public class LdapClientException extends RuntimeException {

    /**
     * Creates a client exception with a message.
     *
     * @param message the error message
     */
    public LdapClientException(String message) {
        super(message);
    }

    /**
     * Creates a client exception with a message and cause.
     *
     * @param message the error message
     * @param cause   the underlying cause
     */
    public LdapClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
