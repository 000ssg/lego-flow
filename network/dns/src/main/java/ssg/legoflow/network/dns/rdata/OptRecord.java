package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/**
 * OPT pseudo-record RDATA for EDNS0 (RFC 6891).
 *
 * <p>The OPT record is a pseudo-record placed in the additional section.
 * It carries extended DNS flags and options. The NAME is always root,
 * the CLASS field carries the UDP payload size, and the TTL field carries
 * extended RCODE and flags (including DNSSEC OK).
 *
 * @param udpPayloadSize the maximum UDP payload size
 * @param extendedRcode  the extended RCODE (upper 8 bits)
 * @param version        the EDNS version (should be 0)
 * @param dnssecOk       the DNSSEC OK (DO) flag
 * @param options        the EDNS options
 * @since 0.1.0
 */
public record OptRecord(
        int udpPayloadSize,
        int extendedRcode,
        int version,
        boolean dnssecOk,
        List<EdnsOption> options
) implements RData {

    public OptRecord {
        Objects.requireNonNull(options, "options must not be null");
        options = Collections.unmodifiableList(new ArrayList<>(options));
    }

    @Override
    public RecordType type() {
        return RecordType.OPT;
    }

    /**
     * Creates a basic OPT record with no options.
     *
     * @param udpPayloadSize the UDP payload size
     * @param dnssecOk       the DO flag
     * @return the OPT record
     * @since 0.1.0
     */
    public static OptRecord of(int udpPayloadSize, boolean dnssecOk) {
        return new OptRecord(udpPayloadSize, 0, 0, dnssecOk, List.of());
    }

    /**
     * Returns the TTL-encoded flags for wire format.
     *
     * @return the 32-bit TTL field value
     * @since 0.1.0
     */
    public int ttlField() {
        int ttl = (extendedRcode & 0xFF) << 24;
        ttl |= (version & 0xFF) << 16;
        if (dnssecOk) {
            ttl |= 0x8000;
        }
        return ttl;
    }

    /**
     * Parses OPT flags from the TTL field.
     *
     * @param ttl the 32-bit TTL value
     * @return the extended rcode, version, and DO flag
     * @since 0.1.0
     */
    public static OptFlags parseTtlField(int ttl) {
        int extRcode = (ttl >> 24) & 0xFF;
        int version = (ttl >> 16) & 0xFF;
        boolean doFlag = (ttl & 0x8000) != 0;
        return new OptFlags(extRcode, version, doFlag);
    }

    /**
     * Parsed OPT TTL flags.
     *
     * @param extendedRcode the extended RCODE
     * @param version       the EDNS version
     * @param dnssecOk      the DO flag
     * @since 0.1.0
     */
    public record OptFlags(int extendedRcode, int version, boolean dnssecOk) {}

    /**
     * An EDNS option (code + data).
     *
     * @param code the option code
     * @param data the option data
     * @since 0.1.0
     */
    public record EdnsOption(int code, byte[] data) {

        /** NSID option code (RFC 5001). */
        public static final int NSID = 3;
        /** DNS Cookie option code (RFC 7873). */
        public static final int COOKIE = 10;

        public EdnsOption {
            data = data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EdnsOption other)) return false;
            return code == other.code && Arrays.equals(data, other.data);
        }

        @Override
        public int hashCode() {
            return 31 * code + Arrays.hashCode(data);
        }
    }
}
