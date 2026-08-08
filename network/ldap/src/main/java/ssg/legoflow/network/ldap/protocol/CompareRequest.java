package ssg.legoflow.network.ldap.protocol;

import java.nio.charset.StandardCharsets;

/**
 * LDAP Compare Request (APPLICATION 14) as defined in RFC 4511 Section 4.10.
 *
 * <pre>{@code
 * CompareRequest ::= [APPLICATION 14] SEQUENCE {
 *     entry LDAPDN,
 *     ava   AttributeValueAssertion
 * }
 * }</pre>
 *
 * @param entry     the DN of the entry to compare
 * @param attribute the attribute description
 * @param value     the assertion value
 * @since 0.1.0
 */
public record CompareRequest(
        String entry,
        String attribute,
        byte[] value
) implements LdapProtocolOp {

    /** APPLICATION tag number for CompareRequest. */
    public static final int TAG = 14;

    /** Creates a compare request with validation. */
    public CompareRequest {
        if (entry == null) throw new IllegalArgumentException("Entry must not be null");
        if (attribute == null) throw new IllegalArgumentException("Attribute must not be null");
        if (value == null) throw new IllegalArgumentException("Value must not be null");
        value = value.clone();
    }

    @Override
    public int tagNumber() { return TAG; }

    /** Returns a copy of the assertion value. */
    @Override
    public byte[] value() { return value.clone(); }

    /**
     * Creates a compare request with a string value.
     *
     * @param entry     the entry DN
     * @param attribute the attribute
     * @param value     the string value
     * @return the request
     */
    public static CompareRequest of(String entry, String attribute, String value) {
        return new CompareRequest(entry, attribute, value.getBytes(StandardCharsets.UTF_8));
    }
}
