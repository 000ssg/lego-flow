package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * LDAP Search Result Entry (APPLICATION 4) as defined in RFC 4511 Section 4.5.2.
 *
 * <pre>{@code
 * SearchResultEntry ::= [APPLICATION 4] SEQUENCE {
 *     objectName  LDAPDN,
 *     attributes  PartialAttributeList
 * }
 * }</pre>
 *
 * @param objectName the DN of the entry
 * @param attributes the entry's attributes
 * @since 0.1.0
 */
public record SearchResultEntry(
        String objectName,
        List<LdapAttribute> attributes
) implements LdapProtocolOp {

    /** APPLICATION tag number for SearchResultEntry. */
    public static final int TAG = 4;

    /**
     * Creates a search result entry with validation.
     */
    public SearchResultEntry {
        if (objectName == null) {
            throw new IllegalArgumentException("Object name must not be null");
        }
        if (attributes == null) {
            throw new IllegalArgumentException("Attributes must not be null");
        }
        attributes = List.copyOf(attributes);
    }

    @Override
    public int tagNumber() {
        return TAG;
    }
}
