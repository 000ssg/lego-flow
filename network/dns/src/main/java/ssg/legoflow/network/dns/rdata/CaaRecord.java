package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.Objects;
/**
 * CAA record RDATA: certification authority authorization (RFC 8659).
 *
 * @param flags the flags byte (bit 0 = issuer critical)
 * @param tag   the property tag (e.g., "issue", "issuewild", "iodef")
 * @param value the property value
 * @since 0.1.0
 */
public record CaaRecord(int flags, String tag, String value) implements RData {

    public CaaRecord {
        Objects.requireNonNull(tag, "tag must not be null");
        Objects.requireNonNull(value, "value must not be null");
        if (flags < 0 || flags > 255) {
            throw new IllegalArgumentException("Flags must be 0-255");
        }
    }

    @Override
    public RecordType type() {
        return RecordType.CAA;
    }

    /**
     * Returns whether the issuer-critical flag is set.
     *
     * @return {@code true} if issuer-critical
     * @since 0.1.0
     */
    public boolean issuerCritical() {
        return (flags & 0x80) != 0;
    }

    /**
     * Creates a CAA record.
     *
     * @param flags the flags
     * @param tag   the tag
     * @param value the value
     * @return the CAA record
     * @since 0.1.0
     */
    public static CaaRecord of(int flags, String tag, String value) {
        return new CaaRecord(flags, tag, value);
    }
}
