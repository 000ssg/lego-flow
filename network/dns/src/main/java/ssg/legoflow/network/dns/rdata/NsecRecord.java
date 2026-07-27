package ssg.legoflow.network.dns.rdata;

import ssg.legoflow.network.dns.protocol.DnsName;
import ssg.legoflow.network.dns.protocol.RecordType;
import ssg.legoflow.network.dns.rdata.dnssec.TypeBitMaps;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * NSEC record RDATA: next secure record (RFC 4034).
 *
 * @param nextDomainName the next owner name in canonical order
 * @param types          the set of record types present at the owner name
 * @since 1.0.0
 */
public record NsecRecord(DnsName nextDomainName, Set<RecordType> types) implements RData {

    public NsecRecord {
        Objects.requireNonNull(nextDomainName, "nextDomainName must not be null");
        Objects.requireNonNull(types, "types must not be null");
        types = Collections.unmodifiableSet(new TreeSet<>(types));
    }

    @Override
    public RecordType type() {
        return RecordType.NSEC;
    }

    /**
     * Encodes the type bit maps for wire format.
     *
     * @return the encoded type bit maps
     * @since 1.0.0
     */
    public byte[] encodeTypeBitMaps() {
        return TypeBitMaps.encode(types);
    }

    /**
     * Decodes type bit maps from wire format.
     *
     * @param data   the raw bytes
     * @param offset the starting offset
     * @param length the length
     * @return the set of record types
     * @since 1.0.0
     */
    public static Set<RecordType> decodeTypeBitMaps(byte[] data, int offset, int length) {
        return TypeBitMaps.decode(data, offset, length);
    }
}
