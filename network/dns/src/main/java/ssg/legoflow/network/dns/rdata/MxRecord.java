package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.util.Objects;

/**
 * MX record RDATA: mail exchange (RFC 1035).
 *
 * @param preference the preference value (lower is higher priority)
 * @param exchange   the mail exchange domain name
 * @since 1.0.0
 */
public record MxRecord(int preference, DnsName exchange) implements RData, Comparable<MxRecord> {

    public MxRecord {
        Objects.requireNonNull(exchange, "exchange must not be null");
        if (preference < 0 || preference > 65535) {
            throw new IllegalArgumentException("Preference must be 0-65535, got " + preference);
        }
    }

    @Override
    public RecordType type() {
        return RecordType.MX;
    }

    @Override
    public int compareTo(MxRecord other) {
        return Integer.compare(this.preference, other.preference);
    }

    /**
     * Creates an MX record from a preference and exchange string.
     *
     * @param preference the preference value
     * @param exchange   the mail exchange domain name
     * @return the MX record
     * @since 1.0.0
     */
    public static MxRecord of(int preference, String exchange) {
        return new MxRecord(preference, DnsName.of(exchange));
    }
}
