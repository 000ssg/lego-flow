package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Unbind Request (APPLICATION 2) as defined in RFC 4511 Section 4.3.
 *
 * <p>The unbind operation has no content; it simply signals the client's
 * intent to close the connection.
 *
 * <pre>{@code
 * UnbindRequest ::= [APPLICATION 2] NULL
 * }</pre>
 *
 * @since 1.0.0
 */
public record UnbindRequest() implements LdapProtocolOp {

    /** APPLICATION tag number for UnbindRequest. */
    public static final int TAG = 2;

    /** Singleton instance. */
    public static final UnbindRequest INSTANCE = new UnbindRequest();

    @Override
    public int tagNumber() {
        return TAG;
    }
}
