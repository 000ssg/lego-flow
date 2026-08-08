package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Modify Response (APPLICATION 7) as defined in RFC 4511 Section 4.6.
 *
 * @param result the LDAP result
 * @since 0.1.0
 */
public record ModifyResponse(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for ModifyResponse. */
    public static final int TAG = 7;

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a successful modify response.
     *
     * @return the response
     */
    public static ModifyResponse success() {
        return new ModifyResponse(LdapResult.success());
    }
}
