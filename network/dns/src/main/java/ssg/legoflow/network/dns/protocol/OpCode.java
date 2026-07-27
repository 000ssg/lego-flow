package ssg.legoflow.network.dns.protocol;

/**
 * DNS operation codes (OPCODE) as defined in RFC 1035 and extensions.
 *
 * @since 1.0.0
 */
public enum OpCode {

    /** Standard query (RFC 1035). */
    QUERY(0),
    /** Inverse query, obsolete (RFC 3425). */
    IQUERY(1),
    /** Server status request (RFC 1035). */
    STATUS(2),
    /** Zone change notification (RFC 1996). */
    NOTIFY(4),
    /** Dynamic update (RFC 2136). */
    UPDATE(5);

    private final int value;

    OpCode(int value) {
        this.value = value;
    }

    /**
     * Returns the 4-bit numeric value for this operation code.
     *
     * @return the opcode value
     * @since 1.0.0
     */
    public int value() {
        return value;
    }

    /**
     * Looks up an {@code OpCode} by its numeric value.
     *
     * @param value the 4-bit opcode value
     * @return the matching operation code
     * @throws IllegalArgumentException if the value is unknown
     * @since 1.0.0
     */
    public static OpCode fromValue(int value) {
        for (OpCode oc : values()) {
            if (oc.value == value) {
                return oc;
            }
        }
        throw new IllegalArgumentException("Unknown DNS opcode: " + value);
    }
}
