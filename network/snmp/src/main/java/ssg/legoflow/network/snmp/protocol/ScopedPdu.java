package ssg.legoflow.network.snmp.protocol;

import java.util.Arrays;

/**
 * SNMPv3 scoped PDU as defined in RFC 3412.
 *
 * <p>A scoped PDU contains a context engine ID, context name, and the
 * actual PDU data. The context identifies which MIB data the PDU refers to.
 *
 * @param contextEngineId the context engine ID (identifies the entity)
 * @param contextName     the context name (identifies the MIB view)
 * @param pdu             the actual PDU
 * @since 0.1.0
 */
public record ScopedPdu(byte[] contextEngineId, String contextName, SnmpPdu pdu) {

    /**
     * Creates a ScopedPdu with validation and defensive copy.
     *
     * @param contextEngineId the context engine ID (must not be null)
     * @param contextName     the context name (must not be null, may be empty)
     * @param pdu             the PDU (must not be null)
     */
    public ScopedPdu {
        if (contextEngineId == null) {
            throw new IllegalArgumentException("Context engine ID must not be null");
        }
        if (contextName == null) {
            throw new IllegalArgumentException("Context name must not be null");
        }
        if (pdu == null) {
            throw new IllegalArgumentException("PDU must not be null");
        }
        contextEngineId = contextEngineId.clone();
    }

    /**
     * Returns a copy of the context engine ID.
     *
     * @return copy of the context engine ID
     */
    @Override
    public byte[] contextEngineId() {
        return contextEngineId.clone();
    }

    /**
     * Creates a ScopedPdu with empty context.
     *
     * @param pdu the PDU
     * @return the scoped PDU with empty context
     */
    public static ScopedPdu of(SnmpPdu pdu) {
        return new ScopedPdu(new byte[0], "", pdu);
    }

    /**
     * Creates a ScopedPdu with the given context engine ID and PDU.
     *
     * @param contextEngineId the context engine ID
     * @param pdu             the PDU
     * @return the scoped PDU
     */
    public static ScopedPdu of(byte[] contextEngineId, SnmpPdu pdu) {
        return new ScopedPdu(contextEngineId, "", pdu);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ScopedPdu other
                && Arrays.equals(contextEngineId, other.contextEngineId)
                && contextName.equals(other.contextName)
                && pdu.equals(other.pdu);
    }

    @Override
    public int hashCode() {
        int h = Arrays.hashCode(contextEngineId);
        h = 31 * h + contextName.hashCode();
        h = 31 * h + pdu.hashCode();
        return h;
    }
}
