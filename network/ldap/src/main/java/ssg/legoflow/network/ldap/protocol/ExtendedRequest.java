package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Extended Request (APPLICATION 23) as defined in RFC 4511 Section 4.12.
 *
 * <pre>{@code
 * ExtendedRequest ::= [APPLICATION 23] SEQUENCE {
 *     requestName  [0] LDAPOID,
 *     requestValue [1] OCTET STRING OPTIONAL
 * }
 * }</pre>
 *
 * @param requestName  the OID identifying the extended operation
 * @param requestValue the optional request value (null if absent)
 * @since 0.1.0
 */
public record ExtendedRequest(
        String requestName,
        byte[] requestValue
) implements LdapProtocolOp {

    /** APPLICATION tag number for ExtendedRequest. */
    public static final int TAG = 23;

    /** StartTLS extended operation OID. */
    public static final String START_TLS_OID = "1.3.6.1.4.1.1466.20037";

    /** Creates an extended request with validation. */
    public ExtendedRequest {
        if (requestName == null) throw new IllegalArgumentException("Request name must not be null");
        if (requestValue != null) requestValue = requestValue.clone();
    }

    @Override
    public int tagNumber() { return TAG; }

    /** Returns a copy of the request value. */
    @Override
    public byte[] requestValue() { return requestValue != null ? requestValue.clone() : null; }

    /**
     * Creates a StartTLS extended request.
     *
     * @return the request
     */
    public static ExtendedRequest startTls() {
        return new ExtendedRequest(START_TLS_OID, null);
    }
}
