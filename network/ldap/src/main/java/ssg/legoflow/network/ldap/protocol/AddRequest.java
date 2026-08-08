package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * LDAP Add Request (APPLICATION 8) as defined in RFC 4511 Section 4.7.
 *
 * <pre>{@code
 * AddRequest ::= [APPLICATION 8] SEQUENCE {
 *     entry      LDAPDN,
 *     attributes AttributeList
 * }
 * }</pre>
 *
 * @param entry      the DN of the entry to add
 * @param attributes the entry's attributes
 * @since 0.1.0
 */
public record AddRequest(
        String entry,
        List<LdapAttribute> attributes
) implements LdapProtocolOp {

    /** APPLICATION tag number for AddRequest. */
    public static final int TAG = 8;

    /** Creates an add request with validation. */
    public AddRequest {
        if (entry == null) throw new IllegalArgumentException("Entry must not be null");
        if (attributes == null) throw new IllegalArgumentException("Attributes must not be null");
        attributes = List.copyOf(attributes);
    }

    @Override
    public int tagNumber() { return TAG; }
}
