package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Compare Response (APPLICATION 15) as defined in RFC 4511 Section 4.10.
 *
 * @param result the LDAP result (COMPARE_TRUE or COMPARE_FALSE)
 * @since 1.0.0
 */
public record CompareResponse(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for CompareResponse. */
    public static final int TAG = 15;

    @Override
    public int tagNumber() { return TAG; }

    /**
     * Creates a compare response indicating TRUE.
     *
     * @return the response
     */
    public static CompareResponse compareTrue() {
        return new CompareResponse(new LdapResult(LdapResultCode.COMPARE_TRUE, "", "", java.util.List.of()));
    }

    /**
     * Creates a compare response indicating FALSE.
     *
     * @return the response
     */
    public static CompareResponse compareFalse() {
        return new CompareResponse(new LdapResult(LdapResultCode.COMPARE_FALSE, "", "", java.util.List.of()));
    }
}
