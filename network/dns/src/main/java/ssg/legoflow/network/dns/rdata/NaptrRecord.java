package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;

import java.util.Objects;

/**
 * NAPTR record RDATA: naming authority pointer (RFC 3403).
 *
 * @param order       the order value (lower processed first)
 * @param preference  the preference value within same order
 * @param flags       control flags (e.g., "u", "s", "a")
 * @param service     the service identifier
 * @param regexp      the regular expression for rewriting
 * @param replacement the replacement domain name
 * @since 1.0.0
 */
public record NaptrRecord(
        int order,
        int preference,
        String flags,
        String service,
        String regexp,
        DnsName replacement
) implements RData {

    public NaptrRecord {
        Objects.requireNonNull(flags, "flags must not be null");
        Objects.requireNonNull(service, "service must not be null");
        Objects.requireNonNull(regexp, "regexp must not be null");
        Objects.requireNonNull(replacement, "replacement must not be null");
    }

    @Override
    public RecordType type() {
        return RecordType.NAPTR;
    }

    /**
     * Creates a NAPTR record.
     *
     * @param order       the order
     * @param preference  the preference
     * @param flags       the flags
     * @param service     the service
     * @param regexp      the regexp
     * @param replacement the replacement domain
     * @return the NAPTR record
     * @since 1.0.0
     */
    public static NaptrRecord of(int order, int preference, String flags,
                                  String service, String regexp, String replacement) {
        return new NaptrRecord(order, preference, flags, service, regexp, DnsName.of(replacement));
    }
}
