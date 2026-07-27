package ssg.legoflow.network.common.ber;

import org.junit.jupiter.api.Test;
import ssg.legoflow.network.common.asn1.Asn1Tag;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link BerTag} encoding and decoding.
 */
class BerTagTest {

    // ── Short-form tag tests ──

    @Test
    void testEncodeShortFormUniversalPrimitive() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(Asn1Tag.BOOLEAN, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x01);
    }

    @Test
    void testEncodeShortFormUniversalConstructed() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(Asn1Tag.SEQUENCE, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x30);
    }

    @Test
    void testEncodeContextSpecificPrimitive() {
        Asn1Tag tag = Asn1Tag.contextSpecific(0, false);
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x80);
    }

    @Test
    void testEncodeContextSpecificConstructed() {
        Asn1Tag tag = Asn1Tag.contextSpecific(3, true);
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0xA3);
    }

    @Test
    void testEncodeApplicationTag() {
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, false, 5);
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x45);
    }

    @Test
    void testEncodePrivateTag() {
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.PRIVATE, true, 10);
        ByteBuffer buf = ByteBuffer.allocate(1);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0xEA);
    }

    @Test
    void testDecodeShortFormTag() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x02});
        Asn1Tag tag = BerTag.decode(buf);
        assertThat(tag.tagClass()).isEqualTo(Asn1Tag.TagClass.UNIVERSAL);
        assertThat(tag.constructed()).isFalse();
        assertThat(tag.number()).isEqualTo(2);
    }

    @Test
    void testDecodeSequenceTag() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x30});
        Asn1Tag tag = BerTag.decode(buf);
        assertThat(tag.tagClass()).isEqualTo(Asn1Tag.TagClass.UNIVERSAL);
        assertThat(tag.constructed()).isTrue();
        assertThat(tag.number()).isEqualTo(0x10);
    }

    @Test
    void testDecodeSetTag() {
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x31});
        Asn1Tag tag = BerTag.decode(buf);
        assertThat(tag.tagClass()).isEqualTo(Asn1Tag.TagClass.UNIVERSAL);
        assertThat(tag.constructed()).isTrue();
        assertThat(tag.number()).isEqualTo(0x11);
    }

    // ── Long-form tag tests ──

    @Test
    void testEncodeLongFormTag31() {
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.UNIVERSAL, false, 31);
        ByteBuffer buf = ByteBuffer.allocate(3);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0x1F);
        assertThat(buf.get() & 0xFF).isEqualTo(31);
    }

    @Test
    void testEncodeLongFormTag128() {
        Asn1Tag tag = new Asn1Tag(Asn1Tag.TagClass.CONTEXT_SPECIFIC, true, 128);
        ByteBuffer buf = ByteBuffer.allocate(4);
        BerTag.encode(tag, buf);
        buf.flip();
        assertThat(buf.get() & 0xFF).isEqualTo(0xBF); // context + constructed + long form
        assertThat(buf.get() & 0xFF).isEqualTo(0x81); // 128 = 1*128 + 0, continuation
        assertThat(buf.get() & 0xFF).isEqualTo(0x00); // last byte
    }

    @Test
    void testRoundTripLongFormTag() {
        Asn1Tag original = new Asn1Tag(Asn1Tag.TagClass.APPLICATION, false, 500);
        ByteBuffer buf = ByteBuffer.allocate(10);
        BerTag.encode(original, buf);
        buf.flip();
        Asn1Tag decoded = BerTag.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testRoundTripAllShortFormTags() {
        for (int i = 0; i < 31; i++) {
            for (Asn1Tag.TagClass tc : Asn1Tag.TagClass.values()) {
                for (boolean constructed : new boolean[]{true, false}) {
                    Asn1Tag original = new Asn1Tag(tc, constructed, i);
                    ByteBuffer buf = ByteBuffer.allocate(5);
                    BerTag.encode(original, buf);
                    buf.flip();
                    Asn1Tag decoded = BerTag.decode(buf);
                    assertThat(decoded).isEqualTo(original);
                }
            }
        }
    }

    @Test
    void testEncodedLength() {
        assertThat(BerTag.encodedLength(Asn1Tag.BOOLEAN)).isEqualTo(1);
        assertThat(BerTag.encodedLength(Asn1Tag.SEQUENCE)).isEqualTo(1);
        assertThat(BerTag.encodedLength(new Asn1Tag(Asn1Tag.TagClass.UNIVERSAL, false, 31))).isEqualTo(2);
        assertThat(BerTag.encodedLength(new Asn1Tag(Asn1Tag.TagClass.UNIVERSAL, false, 128))).isEqualTo(3);
    }

    @Test
    void testDecodeEmptyBufferThrows() {
        ByteBuffer buf = ByteBuffer.allocate(0);
        assertThatThrownBy(() -> BerTag.decode(buf))
                .isInstanceOf(BerDecodingException.class);
    }

    // ── Well-known tag constants ──

    @Test
    void testWellKnownTags() {
        assertThat(Asn1Tag.BOOLEAN.number()).isEqualTo(0x01);
        assertThat(Asn1Tag.INTEGER.number()).isEqualTo(0x02);
        assertThat(Asn1Tag.BIT_STRING.number()).isEqualTo(0x03);
        assertThat(Asn1Tag.OCTET_STRING.number()).isEqualTo(0x04);
        assertThat(Asn1Tag.NULL.number()).isEqualTo(0x05);
        assertThat(Asn1Tag.OBJECT_IDENTIFIER.number()).isEqualTo(0x06);
        assertThat(Asn1Tag.ENUMERATED.number()).isEqualTo(0x0A);
        assertThat(Asn1Tag.UTF8_STRING.number()).isEqualTo(0x0C);
        assertThat(Asn1Tag.PRINTABLE_STRING.number()).isEqualTo(0x13);
        assertThat(Asn1Tag.IA5_STRING.number()).isEqualTo(0x16);
        assertThat(Asn1Tag.GENERALIZED_TIME.number()).isEqualTo(0x18);
        assertThat(Asn1Tag.SEQUENCE.number()).isEqualTo(0x10);
        assertThat(Asn1Tag.SEQUENCE.constructed()).isTrue();
        assertThat(Asn1Tag.SET.number()).isEqualTo(0x11);
        assertThat(Asn1Tag.SET.constructed()).isTrue();
    }

    @Test
    void testTagClassOf() {
        assertThat(Asn1Tag.TagClass.of(0)).isEqualTo(Asn1Tag.TagClass.UNIVERSAL);
        assertThat(Asn1Tag.TagClass.of(1)).isEqualTo(Asn1Tag.TagClass.APPLICATION);
        assertThat(Asn1Tag.TagClass.of(2)).isEqualTo(Asn1Tag.TagClass.CONTEXT_SPECIFIC);
        assertThat(Asn1Tag.TagClass.of(3)).isEqualTo(Asn1Tag.TagClass.PRIVATE);
        assertThatThrownBy(() -> Asn1Tag.TagClass.of(4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTagValidation() {
        assertThatThrownBy(() -> new Asn1Tag(null, false, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Asn1Tag(Asn1Tag.TagClass.UNIVERSAL, false, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
