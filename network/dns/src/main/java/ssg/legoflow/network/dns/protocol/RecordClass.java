package ssg.legoflow.network.dns.protocol;

/**
 * DNS record classes as defined in RFC 1035.
 *
 * @since 0.1.0
 */
public enum RecordClass {

    /** Internet (RFC 1035). */
    IN(1),
    /** Chaos (RFC 1035). */
    CH(3),
    /** Hesiod (RFC 1035). */
    HS(4),
    /** Any class (query only, RFC 1035). */
    ANY(255);

    private final int value;

    RecordClass(int value) {
        this.value = value;
    }

    /**
     * Returns the 16-bit numeric value for this class.
     *
     * @return the class value
     * @since 0.1.0
     */
    public int value() {
        return value;
    }

    /**
     * Looks up a {@code RecordClass} by its numeric value.
     *
     * @param value the 16-bit class value
     * @return the matching record class
     * @throws IllegalArgumentException if the value is unknown
     * @since 0.1.0
     */
    public static RecordClass fromValue(int value) {
        for (RecordClass rc : values()) {
            if (rc.value == value) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown DNS record class: " + value);
    }
}
