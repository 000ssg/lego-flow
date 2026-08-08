package ssg.legoflow.network.dns.protocol;

/**
 * DNS resource record types as defined in RFC 1035 and extensions.
 *
 * <p>Each constant maps to the 16-bit TYPE value carried in DNS questions
 * and resource records.
 *
 * @since 0.1.0
 */
public enum RecordType {

    /** IPv4 host address (RFC 1035). */
    A(1),
    /** Authoritative name server (RFC 1035). */
    NS(2),
    /** Canonical name for an alias (RFC 1035). */
    CNAME(5),
    /** Start of a zone of authority (RFC 1035). */
    SOA(6),
    /** Domain name pointer (RFC 1035). */
    PTR(12),
    /** Mail exchange (RFC 1035). */
    MX(15),
    /** Text strings (RFC 1035). */
    TXT(16),
    /** IPv6 host address (RFC 3596). */
    AAAA(28),
    /** Server selection (RFC 2782). */
    SRV(33),
    /** Naming authority pointer (RFC 3403). */
    NAPTR(35),
    /** EDNS0 pseudo-record (RFC 6891). */
    OPT(41),
    /** Delegation signer (RFC 4034). */
    DS(43),
    /** DNSSEC signature (RFC 4034). */
    RRSIG(46),
    /** Next secure record (RFC 4034). */
    NSEC(47),
    /** DNS public key (RFC 4034). */
    DNSKEY(48),
    /** NSEC3 hashed denial (RFC 5155). */
    NSEC3(50),
    /** NSEC3 parameters (RFC 5155). */
    NSEC3PARAM(51),
    /** Certification Authority Authorization (RFC 8659). */
    CAA(257),
    /** Query for all records (pseudo-type). */
    ANY(255);

    private final int value;

    RecordType(int value) {
        this.value = value;
    }

    /**
     * Returns the 16-bit numeric value for this record type.
     *
     * @return the type value
     * @since 0.1.0
     */
    public int value() {
        return value;
    }

    /**
     * Looks up a {@code RecordType} by its numeric value.
     *
     * @param value the 16-bit type value
     * @return the matching record type
     * @throws IllegalArgumentException if the value is unknown
     * @since 0.1.0
     */
    public static RecordType fromValue(int value) {
        for (RecordType rt : values()) {
            if (rt.value == value) {
                return rt;
            }
        }
        throw new IllegalArgumentException("Unknown DNS record type: " + value);
    }

    /**
     * Looks up a {@code RecordType} by its numeric value, returning {@code null}
     * if the value is not recognized.
     *
     * @param value the 16-bit type value
     * @return the matching record type or {@code null}
     * @since 0.1.0
     */
    public static RecordType fromValueOrNull(int value) {
        for (RecordType rt : values()) {
            if (rt.value == value) {
                return rt;
            }
        }
        return null;
    }
}
