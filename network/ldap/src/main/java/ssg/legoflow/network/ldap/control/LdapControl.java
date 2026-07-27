package ssg.legoflow.network.ldap.control;

/**
 * LDAP control as defined in RFC 4511 Section 4.1.11.
 *
 * <p>Controls provide a mechanism for extending LDAP operations. Each control
 * has an OID identifying its type, a criticality flag, and an optional value.
 *
 * <pre>{@code
 * Control ::= SEQUENCE {
 *     controlType  LDAPOID,
 *     criticality  BOOLEAN DEFAULT FALSE,
 *     controlValue OCTET STRING OPTIONAL
 * }
 * }</pre>
 *
 * @param oid         the control type OID
 * @param criticality whether the control is critical
 * @param value       the optional control value (null if absent)
 * @since 1.0.0
 */
public record LdapControl(String oid, boolean criticality, byte[] value) {

    /**
     * Creates an LDAP control with validation.
     *
     * @param oid         the control type OID (must not be null or empty)
     * @param criticality whether the control is critical
     * @param value       the optional control value
     */
    public LdapControl {
        if (oid == null || oid.isEmpty()) {
            throw new IllegalArgumentException("Control OID must not be null or empty");
        }
        if (value != null) {
            value = value.clone();
        }
    }

    /**
     * Returns a copy of the control value.
     *
     * @return copy of the value, or null if absent
     */
    @Override
    public byte[] value() {
        return value != null ? value.clone() : null;
    }

    /**
     * Creates a control without a value.
     *
     * @param oid         the control type OID
     * @param criticality whether the control is critical
     * @return the control
     */
    public static LdapControl of(String oid, boolean criticality) {
        return new LdapControl(oid, criticality, null);
    }

    /**
     * Creates a control with a value.
     *
     * @param oid         the control type OID
     * @param criticality whether the control is critical
     * @param value       the control value
     * @return the control
     */
    public static LdapControl of(String oid, boolean criticality, byte[] value) {
        return new LdapControl(oid, criticality, value);
    }
}
