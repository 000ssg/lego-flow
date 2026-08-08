package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.RecordType;

import java.util.Arrays;

/**
 * Raw RDATA for record types that are not explicitly modeled.
 *
 * <p>Stores the raw bytes of the RDATA section for unknown or
 * unsupported record types.
 *
 * @param recordType the record type value
 * @param data       the raw RDATA bytes
 * @since 0.1.0
 */
public record RawRData(RecordType recordType, byte[] data) implements RData {

    /**
     * Creates raw RDATA, defensively copying the data.
     *
     * @param recordType the record type
     * @param data       the raw bytes
     */
    public RawRData {
        data = data.clone();
    }

    @Override
    public RecordType type() {
        return recordType;
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof RawRData other)) return false;
        return recordType == other.recordType && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return 31 * recordType.hashCode() + Arrays.hashCode(data);
    }
}
