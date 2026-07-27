package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Search Result Done (APPLICATION 5) as defined in RFC 4511 Section 4.5.2.
 *
 * <p>Indicates the completion of a search operation.
 *
 * @param result the LDAP result
 * @since 1.0.0
 */
public record SearchResultDone(LdapResult result) implements LdapProtocolOp {

    /** APPLICATION tag number for SearchResultDone. */
    public static final int TAG = 5;

    @Override
    public int tagNumber() {
        return TAG;
    }

    /**
     * Creates a successful search result done message.
     *
     * @return the search result done
     */
    public static SearchResultDone success() {
        return new SearchResultDone(LdapResult.success());
    }
}
