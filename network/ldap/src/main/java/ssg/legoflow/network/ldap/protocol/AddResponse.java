package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Add Response (APPLICATION 9) as defined in RFC 4511 Section 4.7.
 *
 * @param result the LDAP result
 * @since 0.1.0
 */
public record AddResponse(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for AddResponse. */
    public static final int TAG = 9;

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a successful add response.
     *
     * @return the response
     */
    public static AddResponse success() {
        return new AddResponse(LdapResult.success());
    }
}
