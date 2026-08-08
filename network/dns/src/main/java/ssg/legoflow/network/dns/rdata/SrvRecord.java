package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.util.Objects;

/**
 * SRV record RDATA: service location (RFC 2782).
 *
 * @param priority the priority (lower is preferred)
 * @param weight   the weight for load balancing among same-priority targets
 * @param port     the TCP/UDP port
 * @param target   the target host
 * @since 0.1.0
 */
public record SrvRecord(int priority, int weight, int port, DnsName target)
        implements RData, Comparable<SrvRecord> {

    public SrvRecord {
        Objects.requireNonNull(target, "target must not be null");
        if (priority < 0 || priority > 65535) {
            throw new IllegalArgumentException("Priority must be 0-65535");
        }
        if (weight < 0 || weight > 65535) {
            throw new IllegalArgumentException("Weight must be 0-65535");
        }
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be 0-65535");
        }
    }

    @Override
    public RecordType type() {
        return RecordType.SRV;
    }

    @Override
    public int compareTo(SrvRecord other) {
        int cmp = Integer.compare(this.priority, other.priority);
        if (cmp != 0) return cmp;
        // Higher weight should come first for selection
        return Integer.compare(other.weight, this.weight);
    }

    /**
     * Creates an SRV record.
     *
     * @param priority the priority
     * @param weight   the weight
     * @param port     the port
     * @param target   the target host name
     * @return the SRV record
     * @since 0.1.0
     */
    public static SrvRecord of(int priority, int weight, int port, String target) {
        return new SrvRecord(priority, weight, port, DnsName.of(target));
    }
}
