package ssg.legoflow.network.common.der;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.ber.BerEncoder;
import ssg.legoflow.network.common.ber.BerLength;
import ssg.legoflow.network.common.ber.BerTag;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * DER (Distinguished Encoding Rules) encoder.
 *
 * <p>DER is a subset of BER with additional constraints that ensure a unique
 * encoding for each value:
 * <ul>
 *   <li>Definite length form only (no indefinite length)</li>
 *   <li>SET elements sorted by tag value</li>
 *   <li>BOOLEAN: false = 0x00, true = 0xFF</li>
 *   <li>BIT STRING: unused bits in the last byte must be zero</li>
 * </ul>
 *
 * <p>For most types, DER encoding is identical to BER with definite length.
 * The main difference is SET element ordering.
 *
 * @since 0.1.0
 */
public final class DerEncoder {

    private DerEncoder() {}

    /**
     * Encodes an ASN.1 type to DER format.
     *
     * @param type the ASN.1 type to encode
     * @return a ByteBuffer positioned at 0 with the DER-encoded bytes
     */
    public static ByteBuffer encode(Asn1Type type) {
        Asn1Type normalized = normalize(type);
        return BerEncoder.encode(normalized);
    }

    /**
     * Encodes an ASN.1 type to DER format into the given buffer.
     *
     * @param type   the ASN.1 type to encode
     * @param buffer the output buffer
     */
    public static void encodeTo(Asn1Type type, ByteBuffer buffer) {
        Asn1Type normalized = normalize(type);
        BerEncoder.encodeTo(normalized, buffer);
    }

    /**
     * Normalizes an ASN.1 type for DER encoding.
     *
     * <p>Sorts SET elements by their encoded tag and recursively normalizes
     * nested structures.
     *
     * @param type the ASN.1 type to normalize
     * @return the normalized type
     */
    public static Asn1Type normalize(Asn1Type type) {
        return switch (type) {
            case Asn1Set set -> normalizeSet(set);
            case Asn1Sequence seq -> normalizeSequence(seq);
            case Asn1ContextSpecific ctx -> normalizeContextSpecific(ctx);
            default -> type;
        };
    }

    private static Asn1Sequence normalizeSequence(Asn1Sequence seq) {
        List<Asn1Type> normalized = seq.elements().stream()
                .map(DerEncoder::normalize)
                .toList();
        return Asn1Sequence.of(normalized);
    }

    private static Asn1Set normalizeSet(Asn1Set set) {
        // Sort elements by their encoded tag value for canonical DER ordering
        List<Asn1Type> sorted = new ArrayList<>(set.elements().stream()
                .map(DerEncoder::normalize)
                .toList());
        sorted.sort(Comparator.comparing(DerEncoder::encodedTagBytes));
        return Asn1Set.of(sorted);
    }

    private static Asn1ContextSpecific normalizeContextSpecific(Asn1ContextSpecific ctx) {
        if (ctx.constructed() && ctx.value() != null) {
            return Asn1ContextSpecific.explicit(ctx.tagNumber(), normalize(ctx.value()));
        }
        return ctx;
    }

    /**
     * Returns a comparable representation of the encoded tag bytes for sorting.
     */
    private static TagSortKey encodedTagBytes(Asn1Type type) {
        Asn1Tag tag = type.tag();
        ByteBuffer buf = ByteBuffer.allocate(BerTag.encodedLength(tag));
        BerTag.encode(tag, buf);
        buf.flip();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        return new TagSortKey(bytes);
    }

    /**
     * Comparable wrapper for tag bytes used in DER SET sorting.
     */
    private record TagSortKey(byte[] bytes) implements Comparable<TagSortKey> {
        @Override
        public int compareTo(TagSortKey other) {
            int len = Math.min(bytes.length, other.bytes.length);
            for (int i = 0; i < len; i++) {
                int cmp = Integer.compare(bytes[i] & 0xFF, other.bytes[i] & 0xFF);
                if (cmp != 0) return cmp;
            }
            return Integer.compare(bytes.length, other.bytes.length);
        }
    }
}
