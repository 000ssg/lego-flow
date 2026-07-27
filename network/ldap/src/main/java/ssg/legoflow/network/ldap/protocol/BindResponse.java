package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Bind Response (APPLICATION 1) as defined in RFC 4511 Section 4.2.2.
 *
 * <pre>{@code
 * BindResponse ::= [APPLICATION 1] SEQUENCE {
 *     COMPONENTS OF LDAPResult,
 *     serverSaslCreds [7] OCTET STRING OPTIONAL
 * }
 * }</pre>
 *
 * @param result          the LDAP result
 * @param serverSaslCreds optional server SASL credentials (null if none)
 * @since 1.0.0
 */
public record BindResponse(
        LdapResult result,
        byte[] serverSaslCreds
) implements LdapProtocolOp {

    /** APPLICATION tag number for BindResponse. */
    public static final int TAG = 1;

    @Override
    public int tagNumber() {
        return TAG;
    }

    /**
     * Creates a successful bind response.
     *
     * @return the bind response
     */
    public static BindResponse success() {
        return new BindResponse(LdapResult.success(), null);
    }

    /**
     * Creates a bind response with the given result.
     *
     * @param result the LDAP result
     * @return the bind response
     */
    public static BindResponse of(LdapResult result) {
        return new BindResponse(result, null);
    }
}
