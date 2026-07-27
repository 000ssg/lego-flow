package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Delete Response (APPLICATION 11) as defined in RFC 4511 Section 4.8.
 *
 * @param result the LDAP result
 * @since 1.0.0
 */
public record DeleteResponse(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for DeleteResponse. */
    public static final int TAG = 11;

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a successful delete response.
     *
     * @return the response
     */
    public static DeleteResponse success() {
        return new DeleteResponse(LdapResult.success());
    }
}
