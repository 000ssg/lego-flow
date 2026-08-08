package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Intermediate Response (APPLICATION 25) as defined in RFC 4511 Section 4.13.
 *
 * <pre>{@code
 * IntermediateResponse ::= [APPLICATION 25] SEQUENCE {
 *     responseName  [0] LDAPOID OPTIONAL,
 *     responseValue [1] OCTET STRING OPTIONAL
 * }
 * }</pre>
 *
 * @param responseName  the optional response OID
 * @param responseValue the optional response value
 * @since 0.1.0
 */
public record IntermediateResponse(
        String responseName,
        byte[] responseValue
) implements LdapProtocolOp {

    /** APPLICATION tag number for IntermediateResponse. */
    public static final int TAG = 25;

    /** Creates an intermediate response. */
    public IntermediateResponse {
        if (responseValue != null) responseValue = responseValue.clone();
    }

    @Override
    public int tagNumber() { return TAG; }

    /** Returns a copy of the response value. */
    @Override
    public byte[] responseValue() { return responseValue != null ? responseValue.clone() : null; }
}
