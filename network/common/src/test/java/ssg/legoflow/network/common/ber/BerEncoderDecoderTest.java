package ssg.legoflow.network.common.ber;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.*;
import ssg.legoflow.network.common.oid.ObjectIdentifier;
import ssg.legoflow.network.common.oid.StandardOids;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Round-trip tests for {@link BerEncoder} and {@link BerDecoder}.
 */
class BerEncoderDecoderTest {

    // ── BOOLEAN ──

    @Test
    void testBooleanTrue() {
        var original = Asn1Boolean.TRUE;
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBooleanFalse() {
        var original = Asn1Boolean.FALSE;
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBooleanEncoding() {
        ByteBuffer encoded = BerEncoder.encode(Asn1Boolean.TRUE);
        assertThat(encoded.remaining()).isEqualTo(3);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01); // tag
        assertThat(encoded.get() & 0xFF).isEqualTo(0x01); // length
        assertThat(encoded.get() & 0xFF).isEqualTo(0xFF); // true = 0xFF
    }

    // ── INTEGER ──

    @Test
    void testIntegerZero() {
        var original = Asn1Integer.of(0);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerPositive() {
        var original = Asn1Integer.of(127);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerPositiveLarge() {
        var original = Asn1Integer.of(128);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerNegative() {
        var original = Asn1Integer.of(-1);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerNegativeLarge() {
        var original = Asn1Integer.of(-128);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerBigPositive() {
        var original = Asn1Integer.of(new BigInteger("123456789012345678901234567890"));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerBigNegative() {
        var original = Asn1Integer.of(new BigInteger("-999999999999999999999"));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerMinimalEncoding() {
        // Zero should encode as single byte 0x00
        ByteBuffer encoded = BerEncoder.encode(Asn1Integer.of(0));
        assertThat(encoded.remaining()).isEqualTo(3); // tag(1) + len(1) + value(1)
    }

    @Test
    void testIntegerOne() {
        var original = Asn1Integer.of(1);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerMaxInt() {
        var original = Asn1Integer.of(Integer.MAX_VALUE);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIntegerMinInt() {
        var original = Asn1Integer.of(Integer.MIN_VALUE);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── BIT STRING ──

    @Test
    void testBitStringEmpty() {
        var original = new Asn1BitString(0, new byte[0]);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBitStringNoUnusedBits() {
        var original = Asn1BitString.of(new byte[]{(byte) 0xFF, (byte) 0xAB});
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBitStringWithUnusedBits() {
        var original = new Asn1BitString(3, new byte[]{(byte) 0xF8});
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testBitStringEncoding() {
        var bs = new Asn1BitString(2, new byte[]{(byte) 0xFC});
        ByteBuffer encoded = BerEncoder.encode(bs);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x03); // BIT STRING tag
        assertThat(encoded.get() & 0xFF).isEqualTo(0x02); // length = 2
        assertThat(encoded.get() & 0xFF).isEqualTo(0x02); // unused bits
        assertThat(encoded.get() & 0xFF).isEqualTo(0xFC); // data
    }

    // ── OCTET STRING ──

    @Test
    void testOctetStringEmpty() {
        var original = Asn1OctetString.of(new byte[0]);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testOctetStringWithData() {
        var original = Asn1OctetString.of("Hello, ASN.1!");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
        assertThat(((Asn1OctetString) decoded).asString()).isEqualTo("Hello, ASN.1!");
    }

    @Test
    void testOctetStringBinary() {
        var original = Asn1OctetString.of(new byte[]{0, 1, 2, (byte) 0xFF, (byte) 0xFE});
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── NULL ──

    @Test
    void testNull() {
        ByteBuffer encoded = BerEncoder.encode(Asn1Null.INSTANCE);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1Null.class);
    }

    @Test
    void testNullEncoding() {
        ByteBuffer encoded = BerEncoder.encode(Asn1Null.INSTANCE);
        assertThat(encoded.remaining()).isEqualTo(2);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x05);
        assertThat(encoded.get() & 0xFF).isEqualTo(0x00);
    }

    // ── OBJECT IDENTIFIER ──

    @Test
    void testOidSimple() {
        var original = Asn1ObjectIdentifier.of("1.3.6.1.2.1.1");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testOidFirstArc0() {
        var original = Asn1ObjectIdentifier.of("0.9.2342.19200300.100.1.1");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testOidFirstArc2() {
        var original = Asn1ObjectIdentifier.of("2.5.4.3");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testOidLargeArcValues() {
        var original = Asn1ObjectIdentifier.of("1.2.840.113549.1.1.11");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testOidSysDescr() {
        var original = new Asn1ObjectIdentifier(StandardOids.SYS_DESCR);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(((Asn1ObjectIdentifier) decoded).oid()).isEqualTo(StandardOids.SYS_DESCR);
    }

    @Test
    void testOidSnmpMib() {
        var original = new Asn1ObjectIdentifier(StandardOids.SNMP_MIB);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(((Asn1ObjectIdentifier) decoded).oid()).isEqualTo(StandardOids.SNMP_MIB);
    }

    @Test
    void testOidRsaEncryption() {
        var original = new Asn1ObjectIdentifier(StandardOids.RSA_ENCRYPTION);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(((Asn1ObjectIdentifier) decoded).oid()).isEqualTo(StandardOids.RSA_ENCRYPTION);
    }

    // ── ENUMERATED ──

    @Test
    void testEnumeratedZero() {
        var original = Asn1Enumerated.of(0);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testEnumeratedPositive() {
        var original = Asn1Enumerated.of(3);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testEnumeratedNegative() {
        var original = Asn1Enumerated.of(-1);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── String types ──

    @Test
    void testUtf8String() {
        var original = Asn1Utf8String.of("Hello World");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testUtf8StringUnicode() {
        var original = Asn1Utf8String.of("Привет мир 你好世界");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testUtf8StringEmpty() {
        var original = Asn1Utf8String.of("");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testPrintableString() {
        var original = Asn1PrintableString.of("Example Corp");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testIA5String() {
        var original = Asn1IA5String.of("user@example.com");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── GeneralizedTime ──

    @Test
    void testGeneralizedTime() {
        var original = Asn1GeneralizedTime.of("20240101120000Z");
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testGeneralizedTimeFromInstant() {
        Instant now = Instant.parse("2024-06-15T10:30:00Z");
        var original = Asn1GeneralizedTime.of(now);
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(((Asn1GeneralizedTime) decoded).value()).isEqualTo(original.value());
    }

    // ── SEQUENCE ──

    @Test
    void testSequenceEmpty() {
        var original = Asn1Sequence.empty();
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testSequenceSingleElement() {
        var original = Asn1Sequence.of(Asn1Integer.of(42));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testSequenceMixedTypes() {
        var original = Asn1Sequence.of(
                Asn1Boolean.TRUE,
                Asn1Integer.of(99),
                Asn1OctetString.of("test"),
                Asn1Null.INSTANCE
        );
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testSequenceNested() {
        var inner = Asn1Sequence.of(
                Asn1Integer.of(1),
                Asn1Integer.of(2)
        );
        var original = Asn1Sequence.of(
                Asn1Boolean.FALSE,
                inner,
                Asn1OctetString.of("nested")
        );
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testSequenceDeeplyNested() {
        var level3 = Asn1Sequence.of(Asn1Integer.of(42));
        var level2 = Asn1Sequence.of(level3, Asn1Boolean.TRUE);
        var level1 = Asn1Sequence.of(level2, Asn1Null.INSTANCE);
        var original = Asn1Sequence.of(level1, Asn1OctetString.of("deep"));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── SET ──

    @Test
    void testSetEmpty() {
        var original = Asn1Set.empty();
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testSetMixedTypes() {
        var original = Asn1Set.of(
                Asn1Integer.of(10),
                Asn1Boolean.TRUE,
                Asn1OctetString.of("set-item")
        );
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    // ── Context-specific ──

    @Test
    void testContextSpecificExplicit() {
        var original = Asn1ContextSpecific.explicit(0, Asn1Integer.of(42));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1ContextSpecific.class);
        var ctx = (Asn1ContextSpecific) decoded;
        assertThat(ctx.tagNumber()).isEqualTo(0);
        assertThat(ctx.constructed()).isTrue();
        assertThat(ctx.value()).isEqualTo(Asn1Integer.of(42));
    }

    @Test
    void testContextSpecificExplicitTag3() {
        var original = Asn1ContextSpecific.explicit(3, Asn1OctetString.of("tagged"));
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1ContextSpecific.class);
        var ctx = (Asn1ContextSpecific) decoded;
        assertThat(ctx.tagNumber()).isEqualTo(3);
        assertThat(ctx.value()).isEqualTo(Asn1OctetString.of("tagged"));
    }

    @Test
    void testContextSpecificImplicit() {
        var original = Asn1ContextSpecific.implicit(1, new byte[]{0x01, 0x02, 0x03});
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1ContextSpecific.class);
        var ctx = (Asn1ContextSpecific) decoded;
        assertThat(ctx.tagNumber()).isEqualTo(1);
        assertThat(ctx.constructed()).isFalse();
        assertThat(ctx.rawBytes()).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void testContextSpecificNestedInSequence() {
        var original = Asn1Sequence.of(
                Asn1Integer.of(1),
                Asn1ContextSpecific.explicit(0, Asn1OctetString.of("optional")),
                Asn1Boolean.TRUE
        );
        ByteBuffer encoded = BerEncoder.encode(original);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(3);
        assertThat(seq.elements().get(0)).isEqualTo(Asn1Integer.of(1));
        assertThat(seq.elements().get(2)).isEqualTo(Asn1Boolean.TRUE);
    }

    // ── Indefinite length ──

    @Test
    void testDecodeIndefiniteLengthSequence() {
        // Manually construct: SEQUENCE (indefinite) { INTEGER(42) } EOC
        ByteBuffer manual = ByteBuffer.allocate(20);
        manual.put((byte) 0x30); // SEQUENCE tag
        manual.put((byte) 0x80); // indefinite length
        // INTEGER 42
        manual.put((byte) 0x02); // INTEGER tag
        manual.put((byte) 0x01); // length 1
        manual.put((byte) 0x2A); // value 42
        // End-of-contents
        manual.put((byte) 0x00);
        manual.put((byte) 0x00);
        manual.flip();

        Asn1Type decoded = BerDecoder.decode(manual);
        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(1);
        assertThat(seq.elements().getFirst()).isEqualTo(Asn1Integer.of(42));
    }

    @Test
    void testDecodeIndefiniteLengthMultipleElements() {
        ByteBuffer manual = ByteBuffer.allocate(30);
        manual.put((byte) 0x30); // SEQUENCE tag
        manual.put((byte) 0x80); // indefinite length
        // BOOLEAN true
        manual.put((byte) 0x01);
        manual.put((byte) 0x01);
        manual.put((byte) 0xFF);
        // INTEGER 7
        manual.put((byte) 0x02);
        manual.put((byte) 0x01);
        manual.put((byte) 0x07);
        // NULL
        manual.put((byte) 0x05);
        manual.put((byte) 0x00);
        // EOC
        manual.put((byte) 0x00);
        manual.put((byte) 0x00);
        manual.flip();

        Asn1Type decoded = BerDecoder.decode(manual);
        assertThat(decoded).isInstanceOf(Asn1Sequence.class);
        var seq = (Asn1Sequence) decoded;
        assertThat(seq.elements()).hasSize(3);
        assertThat(seq.elements().get(0)).isEqualTo(Asn1Boolean.TRUE);
        assertThat(seq.elements().get(1)).isEqualTo(Asn1Integer.of(7));
        assertThat(seq.elements().get(2)).isInstanceOf(Asn1Null.class);
    }

    // ── DecodeAll ──

    @Test
    void testDecodeAll() {
        ByteBuffer buf = ByteBuffer.allocate(100);
        BerEncoder.encodeTo(Asn1Integer.of(1), buf);
        BerEncoder.encodeTo(Asn1Boolean.TRUE, buf);
        BerEncoder.encodeTo(Asn1Null.INSTANCE, buf);
        buf.flip();

        List<Asn1Type> decoded = BerDecoder.decodeAll(buf);
        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0)).isEqualTo(Asn1Integer.of(1));
        assertThat(decoded.get(1)).isEqualTo(Asn1Boolean.TRUE);
        assertThat(decoded.get(2)).isInstanceOf(Asn1Null.class);
    }

    // ── encodedSize ──

    @Test
    void testEncodedSizeMatchesActual() {
        Asn1Type[] types = {
                Asn1Boolean.TRUE,
                Asn1Integer.of(42),
                Asn1Integer.of(new BigInteger("999999999999999")),
                Asn1BitString.of(new byte[]{1, 2, 3}),
                Asn1OctetString.of("test"),
                Asn1Null.INSTANCE,
                Asn1ObjectIdentifier.of("1.3.6.1.2.1.1"),
                Asn1Enumerated.of(5),
                Asn1Utf8String.of("hello"),
                Asn1PrintableString.of("world"),
                Asn1IA5String.of("ia5"),
                Asn1GeneralizedTime.of("20240101120000Z"),
                Asn1Sequence.of(Asn1Integer.of(1), Asn1Boolean.TRUE),
                Asn1Set.of(Asn1Integer.of(1), Asn1Boolean.TRUE),
                Asn1ContextSpecific.explicit(0, Asn1Integer.of(42)),
                Asn1ContextSpecific.implicit(1, new byte[]{1, 2, 3})
        };

        for (Asn1Type type : types) {
            int predicted = BerEncoder.encodedSize(type);
            ByteBuffer encoded = BerEncoder.encode(type);
            assertThat(predicted)
                    .as("Size mismatch for %s", type.getClass().getSimpleName())
                    .isEqualTo(encoded.remaining());
        }
    }

    // ── Builder ──

    @Test
    void testSequenceBuilder() {
        var seq = Asn1Sequence.builder()
                .add(Asn1Integer.of(1))
                .add(Asn1Boolean.TRUE)
                .add(Asn1OctetString.of("built"))
                .build();
        assertThat(seq.elements()).hasSize(3);
        ByteBuffer encoded = BerEncoder.encode(seq);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(seq);
    }

    @Test
    void testSetBuilder() {
        var set = Asn1Set.builder()
                .add(Asn1Integer.of(10))
                .add(Asn1Boolean.FALSE)
                .build();
        assertThat(set.elements()).hasSize(2);
        ByteBuffer encoded = BerEncoder.encode(set);
        Asn1Type decoded = BerDecoder.decode(encoded);
        assertThat(decoded).isEqualTo(set);
    }
}
