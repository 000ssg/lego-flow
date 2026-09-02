package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
/**
 * SOA record RDATA: start of authority (RFC 1035).
 *
 * @param mname   primary name server
 * @param rname   responsible person's mailbox
 * @param serial  zone serial number
 * @param refresh refresh interval in seconds
 * @param retry   retry interval in seconds
 * @param expire  expiration limit in seconds
 * @param minimum minimum TTL in seconds
 * @since 0.1.0
 */
public record SoaRecord(
        DnsName mname,
        DnsName rname,
        long serial,
        int refresh,
        int retry,
        int expire,
        int minimum
) implements RData {

    public SoaRecord {
        Objects.requireNonNull(mname, "mname must not be null");
        Objects.requireNonNull(rname, "rname must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.SOA;
    }

    /**
     * Creates a SOA record from string domain names.
     *
     * @param mname   primary name server
     * @param rname   responsible person's mailbox
     * @param serial  zone serial number
     * @param refresh refresh interval
     * @param retry   retry interval
     * @param expire  expiration limit
     * @param minimum minimum TTL
     * @return the SOA record
     * @since 0.1.0
     */
    public static SoaRecord of(String mname, String rname, long serial,
                                int refresh, int retry, int expire, int minimum) {
        return new SoaRecord(DnsName.of(mname), DnsName.of(rname),
                serial, refresh, retry, expire, minimum);
    }
}
