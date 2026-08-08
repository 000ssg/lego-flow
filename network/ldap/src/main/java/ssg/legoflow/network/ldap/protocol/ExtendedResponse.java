package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Extended Response (APPLICATION 24) as defined in RFC 4511 Section 4.12.
 *
 * <pre>{@code
 * ExtendedResponse ::= [APPLICATION 24] SEQUENCE {
 *     COMPONENTS OF LDAPResult,
 *     responseName  [10] LDAPOID OPTIONAL,
 *     responseValue [11] OCTET STRING OPTIONAL
 * }
 * }</pre>
 *
 * @param result        the LDAP result
 * @param responseName  the optional response OID (null if absent)
 * @param responseValue the optional response value (null if absent)
 * @since 0.1.0
 */
public record ExtendedResponse(
        LdapResult result,
        String responseName,
        byte[] responseValue
) implements LdapProtocolOp {

    /** APPLICATION tag number for ExtendedResponse. */
    public static final int TAG = 24;

    /** Creates an extended response with validation. */
    public ExtendedResponse {
        if (result == null) throw new IllegalArgumentException("Result must not be null");
        if (responseValue != null) responseValue = responseValue.clone();
    }

    @Override
    public int tagNumber() { return TAG; }

    /** Returns a copy of the response value. */
    @Override
    public byte[] responseValue() { return responseValue != null ? responseValue.clone() : null; }

    /**
     * Creates a successful extended response.
     *
     * @return the response
     */
    public static ExtendedResponse success() {
        return new ExtendedResponse(LdapResult.success(), null, null);
    }
}
