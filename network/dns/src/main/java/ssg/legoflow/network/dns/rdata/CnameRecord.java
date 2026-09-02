package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
/**
 * CNAME record RDATA: a canonical name for an alias (RFC 1035).
 *
 * @param cname the canonical domain name
 * @since 0.1.0
 */
public record CnameRecord(DnsName cname) implements RData {

    public CnameRecord {
        Objects.requireNonNull(cname, "cname must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.CNAME;
    }

    /**
     * Creates a CNAME record from a string domain name.
     *
     * @param cname the canonical name
     * @return the CNAME record
     * @since 0.1.0
     */
    public static CnameRecord of(String cname) {
        return new CnameRecord(DnsName.of(cname));
    }
}
