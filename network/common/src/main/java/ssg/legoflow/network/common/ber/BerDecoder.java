package ssg.legoflow.network.common.ber;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.oid.ObjectIdentifier;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes BER (Basic Encoding Rules) encoded bytes into ASN.1 types.
 *
 * <p>Supports both definite and indefinite length encodings. Handles all
 * universal ASN.1 types defined in the {@link Asn1Type} hierarchy, as well
 * as context-specific tagged values.
 *
 * <p>This decoder is stateless and thread-safe.
 *
 * @since 0.1.0
 */
public final class BerDecoder {

    private BerDecoder() {}

    /**
     * Decodes a single ASN.1 type from the buffer.
     *
     * @param buffer the input buffer
     * @return the decoded ASN.1 type
     * @throws BerDecodingException if the data is malformed
     */
    public static Asn1Type decode(ByteBuffer buffer) {
        Asn1Tag tag = BerTag.decode(buffer);
        int length = BerLength.decode(buffer);

        return decodeValue(tag, length, buffer);
    }

    /**
     * Decodes a single ASN.1 type from a byte array.
     *
     * @param data the BER-encoded byte array
     * @return the decoded ASN.1 type
     * @throws BerDecodingException if the data is malformed
     */
    public static Asn1Type decode(byte[] data) {
        return decode(ByteBuffer.wrap(data));
    }

    /**
     * Decodes all ASN.1 types from the buffer until it is exhausted.
     *
     * @param buffer the input buffer
     * @return the list of decoded types
     * @throws BerDecodingException if the data is malformed
     */
    public static List<Asn1Type> decodeAll(ByteBuffer buffer) {
        List<Asn1Type> results = new ArrayList<>();
        while (buffer.hasRemaining()) {
            results.add(decode(buffer));
        }
        return results;
    }

    // ── Private decoding ──

    private static Asn1Type decodeValue(Asn1Tag tag, int length, ByteBuffer buffer) {
        // Context-specific tags
        if (tag.tagClass() == Asn1Tag.TagClass.CONTEXT_SPECIFIC) {
            return decodeContextSpecific(tag, length, buffer);
        }

        // Application or private tags: decode as context-specific-like structure
        if (tag.tagClass() == Asn1Tag.TagClass.APPLICATION || tag.tagClass() == Asn1Tag.TagClass.PRIVATE) {
            return decodeContextSpecific(tag, length, buffer);
        }

        // Universal tags
        if (tag.tagClass() == Asn1Tag.TagClass.UNIVERSAL) {
            return switch (tag.number()) {
                case 0x01 -> decodeBoolean(length, buffer);
                case 0x02 -> decodeInteger(length, buffer);
                case 0x03 -> decodeBitString(length, buffer);
                case 0x04 -> decodeOctetString(length, buffer);
                case 0x05 -> decodeNull(length);
                case 0x06 -> decodeOid(length, buffer);
                case 0x0A -> decodeEnumerated(length, buffer);
                case 0x0C -> decodeUtf8String(length, buffer);
                case 0x13 -> decodePrintableString(length, buffer);
                case 0x16 -> decodeIA5String(length, buffer);
                case 0x18 -> decodeGeneralizedTime(length, buffer);
                case 0x10 -> decodeSequence(length, buffer); // 0x10 + constructed = 0x30
                case 0x11 -> decodeSet(length, buffer);      // 0x11 + constructed = 0x31
                default -> decodeUnknownUniversal(tag, length, buffer);
            };
        }

        throw new BerDecodingException("Unsupported tag: " + tag);
    }

    private static Asn1Boolean decodeBoolean(int length, ByteBuffer buffer) {
        if (length != 1) {
            throw new BerDecodingException("BOOLEAN length must be 1, got: " + length);
        }
        byte val = buffer.get();
        return Asn1Boolean.of(val != 0);
    }

    private static Asn1Integer decodeInteger(int length, ByteBuffer buffer) {
        if (length == 0) {
            throw new BerDecodingException("INTEGER length must be > 0");
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new Asn1Integer(new BigInteger(bytes));
    }

    private static Asn1BitString decodeBitString(int length, ByteBuffer buffer) {
        if (length == 0) {
            throw new BerDecodingException("BIT STRING length must be > 0");
        }
        int unusedBits = buffer.get() & 0xFF;
        byte[] data = new byte[length - 1];
        buffer.get(data);
        return new Asn1BitString(unusedBits, data);
    }

    private static Asn1OctetString decodeOctetString(int length, ByteBuffer buffer) {
        byte[] data = new byte[length];
        buffer.get(data);
        return new Asn1OctetString(data);
    }

    private static Asn1Null decodeNull(int length) {
        if (length != 0) {
            throw new BerDecodingException("NULL length must be 0, got: " + length);
        }
        return Asn1Null.INSTANCE;
    }

    private static Asn1ObjectIdentifier decodeOid(int length, ByteBuffer buffer) {
        if (length == 0) {
            throw new BerDecodingException("OID length must be > 0");
        }
        int endPos = buffer.position() + length;
        List<Integer> arcs = new ArrayList<>();

        // First byte encodes first two arcs: 40 * arc1 + arc2
        int combined = BerUtils.decodeBase128(buffer);
        if (combined < 40) {
            arcs.add(0);
            arcs.add(combined);
        } else if (combined < 80) {
            arcs.add(1);
            arcs.add(combined - 40);
        } else {
            arcs.add(2);
            arcs.add(combined - 80);
        }

        // Remaining arcs
        while (buffer.position() < endPos) {
            arcs.add(BerUtils.decodeBase128(buffer));
        }

        int[] arcArray = arcs.stream().mapToInt(Integer::intValue).toArray();
        return new Asn1ObjectIdentifier(ObjectIdentifier.of(arcArray));
    }

    private static Asn1Enumerated decodeEnumerated(int length, ByteBuffer buffer) {
        if (length == 0) {
            throw new BerDecodingException("ENUMERATED length must be > 0");
        }
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return Asn1Enumerated.of(new BigInteger(bytes).intValue());
    }

    private static Asn1Utf8String decodeUtf8String(int length, ByteBuffer buffer) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return Asn1Utf8String.of(new String(bytes, StandardCharsets.UTF_8));
    }

    private static Asn1PrintableString decodePrintableString(int length, ByteBuffer buffer) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return Asn1PrintableString.of(new String(bytes, StandardCharsets.US_ASCII));
    }

    private static Asn1IA5String decodeIA5String(int length, ByteBuffer buffer) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return Asn1IA5String.of(new String(bytes, StandardCharsets.US_ASCII));
    }

    private static Asn1GeneralizedTime decodeGeneralizedTime(int length, ByteBuffer buffer) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return Asn1GeneralizedTime.of(new String(bytes, StandardCharsets.US_ASCII));
    }

    private static Asn1Sequence decodeSequence(int length, ByteBuffer buffer) {
        if (length == BerLength.INDEFINITE) {
            return decodeSequenceIndefinite(buffer);
        }
        int endPos = buffer.position() + length;
        List<Asn1Type> elements = new ArrayList<>();
        while (buffer.position() < endPos) {
            elements.add(decode(buffer));
        }
        return Asn1Sequence.of(elements);
    }

    private static Asn1Sequence decodeSequenceIndefinite(ByteBuffer buffer) {
        List<Asn1Type> elements = new ArrayList<>();
        while (!BerUtils.isEndOfContents(buffer)) {
            elements.add(decode(buffer));
        }
        BerUtils.consumeEndOfContents(buffer);
        return Asn1Sequence.of(elements);
    }

    private static Asn1Set decodeSet(int length, ByteBuffer buffer) {
        if (length == BerLength.INDEFINITE) {
            return decodeSetIndefinite(buffer);
        }
        int endPos = buffer.position() + length;
        List<Asn1Type> elements = new ArrayList<>();
        while (buffer.position() < endPos) {
            elements.add(decode(buffer));
        }
        return Asn1Set.of(elements);
    }

    private static Asn1Set decodeSetIndefinite(ByteBuffer buffer) {
        List<Asn1Type> elements = new ArrayList<>();
        while (!BerUtils.isEndOfContents(buffer)) {
            elements.add(decode(buffer));
        }
        BerUtils.consumeEndOfContents(buffer);
        return Asn1Set.of(elements);
    }

    private static Asn1ContextSpecific decodeContextSpecific(Asn1Tag tag, int length, ByteBuffer buffer) {
        if (tag.constructed()) {
            if (length == BerLength.INDEFINITE) {
                // Constructed indefinite: read elements until EOC
                List<Asn1Type> elements = new ArrayList<>();
                while (!BerUtils.isEndOfContents(buffer)) {
                    elements.add(decode(buffer));
                }
                BerUtils.consumeEndOfContents(buffer);
                // Wrap as explicit if single inner element
                if (elements.size() == 1) {
                    return Asn1ContextSpecific.explicit(tag.number(), elements.getFirst());
                }
                return Asn1ContextSpecific.explicit(tag.number(), Asn1Sequence.of(elements));
            }
            // Constructed definite: decode inner content
            int endPos = buffer.position() + length;
            List<Asn1Type> elements = new ArrayList<>();
            while (buffer.position() < endPos) {
                elements.add(decode(buffer));
            }
            if (elements.size() == 1) {
                return Asn1ContextSpecific.explicit(tag.number(), elements.getFirst());
            }
            return Asn1ContextSpecific.explicit(tag.number(), Asn1Sequence.of(elements));
        } else {
            // Primitive: raw bytes
            byte[] data = new byte[length];
            buffer.get(data);
            return Asn1ContextSpecific.implicit(tag.number(), data);
        }
    }

    private static Asn1Type decodeUnknownUniversal(Asn1Tag tag, int length, ByteBuffer buffer) {
        // For unknown universal types, treat constructed as sequence-like, primitive as octet string
        if (tag.constructed()) {
            int endPos = buffer.position() + length;
            List<Asn1Type> elements = new ArrayList<>();
            while (buffer.position() < endPos) {
                elements.add(decode(buffer));
            }
            return Asn1Sequence.of(elements);
        } else {
            byte[] data = new byte[length];
            buffer.get(data);
            return new Asn1OctetString(data);
        }
    }
}
