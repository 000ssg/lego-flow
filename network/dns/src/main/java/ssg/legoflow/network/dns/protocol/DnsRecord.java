package ssg.legoflow.network.dns.protocol;

import ssg.legoflow.network.dns.rdata.RData;

import java.util.Objects;

/**
 * A DNS resource record as defined in RFC 1035, Section 4.1.3.
 *
 * @param name        the owner name
 * @param type        the record type
 * @param recordClass the record class
 * @param ttl         the time to live in seconds
 * @param rdata       the record data
 * @since 0.1.0
 */
public record DnsRecord(
        DnsName name,
        RecordType type,
        RecordClass recordClass,
        long ttl,
        RData rdata
) {

    public DnsRecord {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(recordClass, "recordClass must not be null");
        Objects.requireNonNull(rdata, "rdata must not be null");
    }

    /**
     * Creates a record with class IN.
     *
     * @param name  the owner name
     * @param ttl   the TTL
     * @param rdata the record data
     * @return the resource record
     * @since 0.1.0
     */
    public static DnsRecord of(DnsName name, long ttl, RData rdata) {
        return new DnsRecord(name, rdata.type(), RecordClass.IN, ttl, rdata);
    }

    /**
     * Creates a record with class IN from a string name.
     *
     * @param name  the owner name string
     * @param ttl   the TTL
     * @param rdata the record data
     * @return the resource record
     * @since 0.1.0
     */
    public static DnsRecord of(String name, long ttl, RData rdata) {
        return of(DnsName.of(name), ttl, rdata);
    }

    /**
     * Returns a copy of this record with a new TTL.
     *
     * @param newTtl the new TTL
     * @return the record with updated TTL
     * @since 0.1.0
     */
    public DnsRecord withTtl(long newTtl) {
        return new DnsRecord(name, type, recordClass, newTtl, rdata);
    }
}
