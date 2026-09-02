package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
/**
 * PTR record RDATA: a domain name pointer (RFC 1035).
 *
 * @param domainName the pointed-to domain name
 * @since 0.1.0
 */
public record PtrRecord(DnsName domainName) implements RData {

    public PtrRecord {
        Objects.requireNonNull(domainName, "domainName must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.PTR;
    }

    /**
     * Creates a PTR record from a string domain name.
     *
     * @param name the domain name
     * @return the PTR record
     * @since 0.1.0
     */
    public static PtrRecord of(String name) {
        return new PtrRecord(DnsName.of(name));
    }
}
