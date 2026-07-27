package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Modify DN Response (APPLICATION 13) as defined in RFC 4511 Section 4.9.
 *
 * @param result the LDAP result
 * @since 1.0.0
 */
public record ModifyDnResponse(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for ModifyDNResponse. */
    public static final int TAG = 13;

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a successful modify DN response.
     *
     * @return the response
     */
    public static ModifyDnResponse success() {
        return new ModifyDnResponse(LdapResult.success());
    }
}
