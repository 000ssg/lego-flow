package ssg.legoflow.network.dns.protocol;

/**
 * DNS response codes (RCODE) as defined in RFC 1035 and extensions.
 *
 * @since 0.1.0
 */
public enum ResponseCode {

    /** No error (RFC 1035). */
    NOERROR(0),
    /** Format error (RFC 1035). */
    FORMERR(1),
    /** Server failure (RFC 1035). */
    SERVFAIL(2),
    /** Non-existent domain (RFC 1035). */
    NXDOMAIN(3),
    /** Not implemented (RFC 1035). */
    NOTIMP(4),
    /** Query refused (RFC 1035). */
    REFUSED(5),
    /** Name exists when it should not (RFC 2136). */
    YXDOMAIN(6),
    /** RR set exists when it should not (RFC 2136). */
    YXRRSET(7),
    /** RR set that should exist does not (RFC 2136). */
    NXRRSET(8),
    /** Server not authoritative / not authorized (RFC 2136). */
    NOTAUTH(9),
    /** Name not contained in zone (RFC 2136). */
    NOTZONE(10);

    private final int value;

    ResponseCode(int value) {
        this.value = value;
    }

    /**
     * Returns the 4-bit numeric value for this response code.
     *
     * @return the response code value
     * @since 0.1.0
     */
    public int value() {
        return value;
    }

    /**
     * Looks up a {@code ResponseCode} by its numeric value.
     *
     * @param value the RCODE value
     * @return the matching response code
     * @throws IllegalArgumentException if the value is unknown
     * @since 0.1.0
     */
    public static ResponseCode fromValue(int value) {
        for (ResponseCode rc : values()) {
            if (rc.value == value) {
                return rc;
            }
        }
        throw new IllegalArgumentException("Unknown DNS response code: " + value);
    }
}
