package ssg.legoflow.network.common.ber;

import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.oid.ObjectIdentifier;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Encodes ASN.1 types to BER (Basic Encoding Rules) byte representation.
 *
 * <p>Produces a {@link ByteBuffer} containing the TLV (Tag-Length-Value) encoded
 * form of any {@link Asn1Type}. Uses definite length encoding by default.
 *
 * <p>This encoder is stateless and thread-safe.
 *
 * @since 1.0.0
 */
public final class BerEncoder {

    private BerEncoder() {}

    /**
     * Encodes an ASN.1 type to a new ByteBuffer.
     *
     * @param type the ASN.1 type to encode
     * @return a ByteBuffer positioned at 0 with the encoded bytes
     */
    public static ByteBuffer encode(Asn1Type type) {
        int size = encodedSize(type);
        ByteBuffer buffer = ByteBuffer.allocate(size);
        encodeTo(type, buffer);
        buffer.flip();
        return buffer;
    }

    /**
     * Encodes an ASN.1 type into the given buffer at its current position.
     *
     * @param type   the ASN.1 type to encode
     * @param buffer the output buffer
     */
    public static void encodeTo(Asn1Type type, ByteBuffer buffer) {
        switch (type) {
            case Asn1Boolean b -> encodeBoolean(b, buffer);
            case Asn1Integer i -> encodeInteger(i, buffer);
            case Asn1BitString bs -> encodeBitString(bs, buffer);
            case Asn1OctetString os -> encodeOctetString(os, buffer);
            case Asn1Null n -> encodeNull(buffer);
            case Asn1ObjectIdentifier oid -> encodeOid(oid, buffer);
            case Asn1Enumerated e -> encodeEnumerated(e, buffer);
            case Asn1Utf8String s -> encodeStringType(Asn1Tag.UTF8_STRING, s.value(), buffer);
            case Asn1PrintableString s -> encodeStringType(Asn1Tag.PRINTABLE_STRING, s.value(), buffer);
            case Asn1IA5String s -> encodeStringType(Asn1Tag.IA5_STRING, s.value(), buffer);
            case Asn1GeneralizedTime t -> encodeStringType(Asn1Tag.GENERALIZED_TIME, t.value(), buffer);
            case Asn1Sequence seq -> encodeSequence(seq, buffer);
            case Asn1Set set -> encodeSet(set, buffer);
            case Asn1ContextSpecific ctx -> encodeContextSpecific(ctx, buffer);
        }
    }

    /**
     * Returns the total encoded size in bytes for the given ASN.1 type.
     *
     * @param type the ASN.1 type
     * @return the total TLV size in bytes
     */
    public static int encodedSize(Asn1Type type) {
        return switch (type) {
            case Asn1Boolean _ -> 3; // tag(1) + length(1) + value(1)
            case Asn1Integer i -> {
                byte[] bytes = i.value().toByteArray();
                yield BerTag.encodedLength(Asn1Tag.INTEGER) + BerLength.encodedLength(bytes.length) + bytes.length;
            }
            case Asn1BitString bs -> {
                int contentLen = 1 + bs.data().length; // unused bits byte + data
                yield BerTag.encodedLength(Asn1Tag.BIT_STRING) + BerLength.encodedLength(contentLen) + contentLen;
            }
            case Asn1OctetString os -> {
                int len = os.value().length;
                yield BerTag.encodedLength(Asn1Tag.OCTET_STRING) + BerLength.encodedLength(len) + len;
            }
            case Asn1Null _ -> 2; // tag(1) + length(1)=0
            case Asn1ObjectIdentifier oid -> {
                int contentLen = oidContentLength(oid.oid());
                yield BerTag.encodedLength(Asn1Tag.OBJECT_IDENTIFIER) + BerLength.encodedLength(contentLen) + contentLen;
            }
            case Asn1Enumerated e -> {
                byte[] bytes = BigInteger.valueOf(e.value()).toByteArray();
                yield BerTag.encodedLength(Asn1Tag.ENUMERATED) + BerLength.encodedLength(bytes.length) + bytes.length;
            }
            case Asn1Utf8String s -> stringSize(Asn1Tag.UTF8_STRING, s.value());
            case Asn1PrintableString s -> stringSize(Asn1Tag.PRINTABLE_STRING, s.value());
            case Asn1IA5String s -> stringSize(Asn1Tag.IA5_STRING, s.value());
            case Asn1GeneralizedTime t -> stringSize(Asn1Tag.GENERALIZED_TIME, t.value());
            case Asn1Sequence seq -> {
                int contentLen = seq.elements().stream().mapToInt(BerEncoder::encodedSize).sum();
                yield BerTag.encodedLength(Asn1Tag.SEQUENCE) + BerLength.encodedLength(contentLen) + contentLen;
            }
            case Asn1Set set -> {
                int contentLen = set.elements().stream().mapToInt(BerEncoder::encodedSize).sum();
                yield BerTag.encodedLength(Asn1Tag.SET) + BerLength.encodedLength(contentLen) + contentLen;
            }
            case Asn1ContextSpecific ctx -> contextSpecificSize(ctx);
        };
    }

    // ── Private encoding methods ──

    private static void encodeBoolean(Asn1Boolean b, ByteBuffer buffer) {
        BerTag.encode(Asn1Tag.BOOLEAN, buffer);
        BerLength.encode(1, buffer);
        buffer.put(b.value() ? (byte) 0xFF : (byte) 0x00);
    }

    private static void encodeInteger(Asn1Integer i, ByteBuffer buffer) {
        byte[] bytes = i.value().toByteArray();
        BerTag.encode(Asn1Tag.INTEGER, buffer);
        BerLength.encode(bytes.length, buffer);
        buffer.put(bytes);
    }

    private static void encodeBitString(Asn1BitString bs, ByteBuffer buffer) {
        byte[] data = bs.data();
        int contentLen = 1 + data.length;
        BerTag.encode(Asn1Tag.BIT_STRING, buffer);
        BerLength.encode(contentLen, buffer);
        buffer.put((byte) bs.unusedBits());
        buffer.put(data);
    }

    private static void encodeOctetString(Asn1OctetString os, ByteBuffer buffer) {
        byte[] val = os.value();
        BerTag.encode(Asn1Tag.OCTET_STRING, buffer);
        BerLength.encode(val.length, buffer);
        buffer.put(val);
    }

    private static void encodeNull(ByteBuffer buffer) {
        BerTag.encode(Asn1Tag.NULL, buffer);
        BerLength.encode(0, buffer);
    }

    private static void encodeOid(Asn1ObjectIdentifier oidType, ByteBuffer buffer) {
        ObjectIdentifier oid = oidType.oid();
        int contentLen = oidContentLength(oid);
        BerTag.encode(Asn1Tag.OBJECT_IDENTIFIER, buffer);
        BerLength.encode(contentLen, buffer);

        // First two arcs combined: 40 * arc1 + arc2
        int combined = 40 * oid.arc(0) + oid.arc(1);
        BerUtils.encodeBase128(combined, buffer);

        // Remaining arcs
        for (int i = 2; i < oid.size(); i++) {
            BerUtils.encodeBase128(oid.arc(i), buffer);
        }
    }

    private static void encodeEnumerated(Asn1Enumerated e, ByteBuffer buffer) {
        byte[] bytes = BigInteger.valueOf(e.value()).toByteArray();
        BerTag.encode(Asn1Tag.ENUMERATED, buffer);
        BerLength.encode(bytes.length, buffer);
        buffer.put(bytes);
    }

    private static void encodeStringType(Asn1Tag tag, String value, ByteBuffer buffer) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        BerTag.encode(tag, buffer);
        BerLength.encode(bytes.length, buffer);
        buffer.put(bytes);
    }

    private static void encodeSequence(Asn1Sequence seq, ByteBuffer buffer) {
        int contentLen = seq.elements().stream().mapToInt(BerEncoder::encodedSize).sum();
        BerTag.encode(Asn1Tag.SEQUENCE, buffer);
        BerLength.encode(contentLen, buffer);
        for (Asn1Type element : seq.elements()) {
            encodeTo(element, buffer);
        }
    }

    private static void encodeSet(Asn1Set set, ByteBuffer buffer) {
        int contentLen = set.elements().stream().mapToInt(BerEncoder::encodedSize).sum();
        BerTag.encode(Asn1Tag.SET, buffer);
        BerLength.encode(contentLen, buffer);
        for (Asn1Type element : set.elements()) {
            encodeTo(element, buffer);
        }
    }

    private static void encodeContextSpecific(Asn1ContextSpecific ctx, ByteBuffer buffer) {
        Asn1Tag tag = ctx.tag();
        if (ctx.constructed() && ctx.value() != null) {
            // Explicit: wrap the inner value
            int contentLen = encodedSize(ctx.value());
            BerTag.encode(tag, buffer);
            BerLength.encode(contentLen, buffer);
            encodeTo(ctx.value(), buffer);
        } else {
            byte[] raw = ctx.rawBytes();
            if (raw != null) {
                // Implicit primitive: raw bytes
                BerTag.encode(tag, buffer);
                BerLength.encode(raw.length, buffer);
                buffer.put(raw);
            } else {
                // Empty constructed
                BerTag.encode(tag, buffer);
                BerLength.encode(0, buffer);
            }
        }
    }

    // ── Size helpers ──

    private static int stringSize(Asn1Tag tag, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return BerTag.encodedLength(tag) + BerLength.encodedLength(bytes.length) + bytes.length;
    }

    private static int oidContentLength(ObjectIdentifier oid) {
        int combined = 40 * oid.arc(0) + oid.arc(1);
        int len = BerUtils.base128Length(combined);
        for (int i = 2; i < oid.size(); i++) {
            len += BerUtils.base128Length(oid.arc(i));
        }
        return len;
    }

    private static int contextSpecificSize(Asn1ContextSpecific ctx) {
        Asn1Tag tag = ctx.tag();
        if (ctx.constructed() && ctx.value() != null) {
            int contentLen = encodedSize(ctx.value());
            return BerTag.encodedLength(tag) + BerLength.encodedLength(contentLen) + contentLen;
        } else {
            byte[] raw = ctx.rawBytes();
            if (raw != null) {
                return BerTag.encodedLength(tag) + BerLength.encodedLength(raw.length) + raw.length;
            } else {
                return BerTag.encodedLength(tag) + BerLength.encodedLength(0);
            }
        }
    }
}
