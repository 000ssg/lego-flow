package ssg.legoflow.network.ldap.protocol;

import java.util.List;

/**
 * An LDAP attribute consisting of a type (description) and a set of values.
 *
 * <pre>{@code
 * PartialAttribute ::= SEQUENCE {
 *     type  AttributeDescription,
 *     vals  SET OF AttributeValue
 * }
 * }</pre>
 *
 * @param type   the attribute description (e.g. "cn", "objectClass")
 * @param values the attribute values
 * @since 0.1.0
 */
public record LdapAttribute(String type, List<byte[]> values) {

    /**
     * Creates an LDAP attribute with validation.
     */
    public LdapAttribute {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Attribute type must not be null or empty");
        }
        if (values == null) {
            throw new IllegalArgumentException("Values must not be null");
        }
        values = List.copyOf(values);
    }

    /**
     * Creates an attribute from string values.
     *
     * @param type   the attribute type
     * @param values the string values
     * @return the attribute
     */
    public static LdapAttribute of(String type, String... values) {
        List<byte[]> byteValues = java.util.Arrays.stream(values)
                .map(v -> v.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .toList();
        return new LdapAttribute(type, byteValues);
    }

    /**
     * Returns the first value as a UTF-8 string.
     *
     * @return the first value, or null if no values exist
     */
    public String firstValueAsString() {
        return values.isEmpty() ? null : new String(values.getFirst(), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Returns all values as UTF-8 strings.
     *
     * @return the values as strings
     */
    public List<String> valuesAsStrings() {
        return values.stream()
                .map(v -> new String(v, java.nio.charset.StandardCharsets.UTF_8))
                .toList();
    }
}
