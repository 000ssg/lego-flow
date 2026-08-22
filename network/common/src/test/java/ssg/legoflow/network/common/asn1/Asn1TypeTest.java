package ssg.legoflow.network.common.asn1;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.time.Instant;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for ASN.1 type value objects.
 */
class Asn1TypeTest {

    // ── Asn1Boolean ──

    @Test
    void testBooleanOf() {
        assertThat(Asn1Boolean.of(true)).isSameAs(Asn1Boolean.TRUE);
        assertThat(Asn1Boolean.of(false)).isSameAs(Asn1Boolean.FALSE);
    }

    @Test
    void testBooleanTag() {
        assertThat(Asn1Boolean.TRUE.tag()).isEqualTo(Asn1Tag.BOOLEAN);
    }

    // ── Asn1Integer ──

    @Test
    void testIntegerOfLong() {
        var i = Asn1Integer.of(42);
        assertThat(i.value()).isEqualTo(BigInteger.valueOf(42));
    }

    @Test
    void testIntegerOfBigInteger() {
        var big = new BigInteger("999999999999999999999");
        var i = Asn1Integer.of(big);
        assertThat(i.value()).isEqualTo(big);
    }

    @Test
    void testIntegerNullThrows() {
        assertThatThrownBy(() -> new Asn1Integer(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIntegerTag() {
        assertThat(Asn1Integer.of(0).tag()).isEqualTo(Asn1Tag.INTEGER);
    }

    // ── Asn1BitString ──

    @Test
    void testBitStringOf() {
        var bs = Asn1BitString.of(new byte[]{1, 2, 3});
        assertThat(bs.unusedBits()).isEqualTo(0);
        assertThat(bs.data()).containsExactly(1, 2, 3);
    }

    @Test
    void testBitStringDefensiveCopy() {
        byte[] original = {1, 2, 3};
        var bs = Asn1BitString.of(original);
        original[0] = 99;
        assertThat(bs.data()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testBitStringInvalidUnusedBits() {
        assertThatThrownBy(() -> new Asn1BitString(8, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Asn1BitString(-1, new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBitStringEmptyWithUnusedBitsThrows() {
        assertThatThrownBy(() -> new Asn1BitString(1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBitStringNullDataThrows() {
        assertThatThrownBy(() -> new Asn1BitString(0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBitStringEquality() {
        var a = new Asn1BitString(2, new byte[]{(byte) 0xFC});
        var b = new Asn1BitString(2, new byte[]{(byte) 0xFC});
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── Asn1OctetString ──

    @Test
    void testOctetStringOfString() {
        var os = Asn1OctetString.of("hello");
        assertThat(os.asString()).isEqualTo("hello");
    }

    @Test
    void testOctetStringDefensiveCopy() {
        byte[] original = {1, 2, 3};
        var os = Asn1OctetString.of(original);
        original[0] = 99;
        assertThat(os.value()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testOctetStringNullThrows() {
        assertThatThrownBy(() -> new Asn1OctetString(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testOctetStringEquality() {
        var a = Asn1OctetString.of("test");
        var b = Asn1OctetString.of("test");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── Asn1Null ──

    @Test
    void testNullSingleton() {
        assertThat(Asn1Null.INSTANCE.tag()).isEqualTo(Asn1Tag.NULL);
    }

    // ── Asn1ObjectIdentifier ──

    @Test
    void testObjectIdentifierOf() {
        var oid = Asn1ObjectIdentifier.of("1.3.6.1");
        assertThat(oid.oid().toDottedString()).isEqualTo("1.3.6.1");
    }

    @Test
    void testObjectIdentifierNullThrows() {
        assertThatThrownBy(() -> new Asn1ObjectIdentifier(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Asn1Enumerated ──

    @Test
    void testEnumeratedOf() {
        var e = Asn1Enumerated.of(5);
        assertThat(e.value()).isEqualTo(5);
        assertThat(e.tag()).isEqualTo(Asn1Tag.ENUMERATED);
    }

    // ── String types ──

    @Test
    void testUtf8StringNullThrows() {
        assertThatThrownBy(() -> new Asn1Utf8String(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testPrintableStringNullThrows() {
        assertThatThrownBy(() -> new Asn1PrintableString(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testIA5StringNullThrows() {
        assertThatThrownBy(() -> new Asn1IA5String(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── GeneralizedTime ──

    @Test
    void testGeneralizedTimeFromInstant() {
        Instant instant = Instant.parse("2024-01-15T10:30:00Z");
        var gt = Asn1GeneralizedTime.of(instant);
        assertThat(gt.value()).isEqualTo("20240115103000Z");
    }

    @Test
    void testGeneralizedTimeToInstant() {
        var gt = Asn1GeneralizedTime.of("20240615120000Z");
        Instant instant = gt.toInstant();
        assertThat(instant).isEqualTo(Instant.parse("2024-06-15T12:00:00Z"));
    }

    @Test
    void testGeneralizedTimeNullThrows() {
        assertThatThrownBy(() -> new Asn1GeneralizedTime(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Asn1Sequence ──

    @Test
    void testSequenceOf() {
        var seq = Asn1Sequence.of(Asn1Integer.of(1), Asn1Boolean.TRUE);
        assertThat(seq.elements()).hasSize(2);
        assertThat(seq.tag()).isEqualTo(Asn1Tag.SEQUENCE);
    }

    @Test
    void testSequenceEmpty() {
        var seq = Asn1Sequence.empty();
        assertThat(seq.elements()).isEmpty();
    }

    @Test
    void testSequenceImmutable() {
        var seq = Asn1Sequence.of(Asn1Integer.of(1));
        assertThatThrownBy(() -> seq.elements().add(Asn1Boolean.TRUE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testSequenceNullThrows() {
        assertThatThrownBy(() -> new Asn1Sequence(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Asn1Set ──

    @Test
    void testSetOf() {
        var set = Asn1Set.of(Asn1Integer.of(1), Asn1Boolean.TRUE);
        assertThat(set.elements()).hasSize(2);
        assertThat(set.tag()).isEqualTo(Asn1Tag.SET);
    }

    @Test
    void testSetNullThrows() {
        assertThatThrownBy(() -> new Asn1Set(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Asn1ContextSpecific ──

    @Test
    void testContextSpecificExplicit() {
        var ctx = Asn1ContextSpecific.explicit(0, Asn1Integer.of(42));
        assertThat(ctx.tagNumber()).isEqualTo(0);
        assertThat(ctx.constructed()).isTrue();
        assertThat(ctx.value()).isEqualTo(Asn1Integer.of(42));
        assertThat(ctx.rawBytes()).isNull();
    }

    @Test
    void testContextSpecificImplicit() {
        var ctx = Asn1ContextSpecific.implicit(1, new byte[]{1, 2});
        assertThat(ctx.tagNumber()).isEqualTo(1);
        assertThat(ctx.constructed()).isFalse();
        assertThat(ctx.value()).isNull();
        assertThat(ctx.rawBytes()).containsExactly(1, 2);
    }

    @Test
    void testContextSpecificNegativeTagThrows() {
        assertThatThrownBy(() -> Asn1ContextSpecific.explicit(-1, Asn1Null.INSTANCE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testContextSpecificTag() {
        var ctx = Asn1ContextSpecific.explicit(3, Asn1Null.INSTANCE);
        assertThat(ctx.tag().tagClass()).isEqualTo(Asn1Tag.TagClass.CONTEXT_SPECIFIC);
        assertThat(ctx.tag().number()).isEqualTo(3);
        assertThat(ctx.tag().constructed()).isTrue();
    }

    @Test
    void testContextSpecificEquality() {
        var a = Asn1ContextSpecific.implicit(1, new byte[]{1, 2, 3});
        var b = Asn1ContextSpecific.implicit(1, new byte[]{1, 2, 3});
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void testContextSpecificDefensiveCopy() {
        byte[] data = {1, 2, 3};
        var ctx = Asn1ContextSpecific.implicit(0, data);
        data[0] = 99;
        assertThat(ctx.rawBytes()[0]).isEqualTo((byte) 1);
    }

    // ── Sealed interface ──

    @Test
    void testSealedInterfacePermits() {
        // Verify all expected types implement Asn1Type
        Asn1Type[] types = {
                Asn1Boolean.TRUE,
                Asn1Integer.of(0),
                Asn1BitString.of(new byte[0]),
                Asn1OctetString.of(new byte[0]),
                Asn1Null.INSTANCE,
                Asn1ObjectIdentifier.of("1.2"),
                Asn1Enumerated.of(0),
                Asn1Utf8String.of(""),
                Asn1PrintableString.of(""),
                Asn1IA5String.of(""),
                Asn1GeneralizedTime.of("20240101120000Z"),
                Asn1Sequence.empty(),
                Asn1Set.empty(),
                Asn1ContextSpecific.explicit(0, Asn1Null.INSTANCE)
        };
        for (Asn1Type type : types) {
            assertThat(type).isInstanceOf(Asn1Type.class);
            assertThat(type.tag()).isNotNull();
        }
    }

    // ── Pattern matching ──

    @Test
    void testPatternMatchingSwitch() {
        Asn1Type type = Asn1Integer.of(42);
        String result = switch (type) {
            case Asn1Boolean b -> "bool:" + b.value();
            case Asn1Integer i -> "int:" + i.value();
            case Asn1BitString bs -> "bits";
            case Asn1OctetString os -> "octets";
            case Asn1Null n -> "null";
            case Asn1ObjectIdentifier oid -> "oid:" + oid.oid();
            case Asn1Enumerated e -> "enum:" + e.value();
            case Asn1Utf8String s -> "utf8:" + s.value();
            case Asn1PrintableString s -> "print:" + s.value();
            case Asn1IA5String s -> "ia5:" + s.value();
            case Asn1GeneralizedTime t -> "time:" + t.value();
            case Asn1Sequence seq -> "seq:" + seq.elements().size();
            case Asn1Set set -> "set:" + set.elements().size();
            case Asn1ContextSpecific ctx -> "ctx:" + ctx.tagNumber();
        };
        assertThat(result).isEqualTo("int:42");
    }
}
