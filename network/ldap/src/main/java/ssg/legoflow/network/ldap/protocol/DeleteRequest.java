package ssg.legoflow.network.ldap.protocol;

/**
 * LDAP Delete Request (APPLICATION 10) as defined in RFC 4511 Section 4.8.
 *
 * <pre>{@code
 * DelRequest ::= [APPLICATION 10] LDAPDN
 * }</pre>
 *
 * @param entry the DN of the entry to delete
 * @since 1.0.0
 */
public record DeleteRequest(String entry) implements LdapProtocolOp {

    /** APPLICATION tag number for DeleteRequest. */
    public static final int TAG = 10;

    /** Creates a delete request with validation. */
    public DeleteRequest {
        if (entry == null) throw new IllegalArgumentException("Entry must not be null");
    }

    @Override
    public int tagNumber() { return TAG; }
}
