package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
/**
 * NS record RDATA: an authoritative name server (RFC 1035).
 *
 * @param nameServer the name server domain name
 * @since 0.1.0
 */
public record NsRecord(DnsName nameServer) implements RData {

    public NsRecord {
        Objects.requireNonNull(nameServer, "nameServer must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.NS;
    }

    /**
     * Creates an NS record from a string domain name.
     *
     * @param ns the name server domain name
     * @return the NS record
     * @since 0.1.0
     */
    public static NsRecord of(String ns) {
        return new NsRecord(DnsName.of(ns));
    }
}
