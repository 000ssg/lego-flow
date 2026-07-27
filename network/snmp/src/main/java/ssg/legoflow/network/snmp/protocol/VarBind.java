package ssg.legoflow.network.snmp.protocol;

import ssg.legoflow.network.common.oid.ObjectIdentifier;

/**
 * An SNMP variable binding (VarBind) — an OID paired with a value.
 *
 * <p>Used in PDUs to represent managed object name-value pairs.
 * In GetRequest PDUs the value is typically {@link SnmpValue.Null}.
 *
 * @param oid   the object identifier
 * @param value the associated value
 * @since 1.0.0
 */
public record VarBind(ObjectIdentifier oid, SnmpValue value) {

    /**
     * Creates a VarBind with validation.
     *
     * @param oid   the object identifier (must not be null)
     * @param value the value (must not be null)
     */
    public VarBind {
        if (oid == null) {
            throw new IllegalArgumentException("OID must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    /**
     * Creates a VarBind with a null value (for GET requests).
     *
     * @param oid the object identifier
     * @return the VarBind with null value
     */
    public static VarBind ofNull(ObjectIdentifier oid) {
        return new VarBind(oid, SnmpValue.Null.INSTANCE);
    }

    /**
     * Creates a VarBind with a null value from dotted OID string.
     *
     * @param oid the dotted OID string
     * @return the VarBind with null value
     */
    public static VarBind ofNull(String oid) {
        return new VarBind(ObjectIdentifier.parse(oid), SnmpValue.Null.INSTANCE);
    }
}
