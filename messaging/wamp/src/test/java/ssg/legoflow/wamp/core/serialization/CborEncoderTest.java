package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Byte-by-byte verification of CBOR encoding against RFC 8949.
 */
class CborEncoderTest {

    private final CborEncoder encoder = new CborEncoder();

    @Test
    void testEncodeNull() {
        assertThat(encoder.encode(null)).containsExactly((byte) 0xf6);
    }

    @Test
    void testEncodeTrue() {
        assertThat(encoder.encode(true)).containsExactly((byte) 0xf5);
    }

    @Test
    void testEncodeFalse() {
        assertThat(encoder.encode(false)).containsExactly((byte) 0xf4);
    }

    @Test
    void testEncodeSmallUnsigned() {
        // 0 -> 0x00
        assertThat(encoder.encode(0)).containsExactly((byte) 0x00);
        // 1 -> 0x01
        assertThat(encoder.encode(1)).containsExactly((byte) 0x01);
        // 23 -> 0x17
        assertThat(encoder.encode(23)).containsExactly((byte) 0x17);
    }

    @Test
    void testEncodeUnsigned1Byte() {
        // 24 -> 0x18 0x18
        assertThat(encoder.encode(24)).containsExactly((byte) 0x18, (byte) 0x18);
        // 255 -> 0x18 0xff
        assertThat(encoder.encode(255)).containsExactly((byte) 0x18, (byte) 0xff);
    }

    @Test
    void testEncodeUnsigned2Bytes() {
        // 256 -> 0x19 0x01 0x00
        assertThat(encoder.encode(256)).containsExactly((byte) 0x19, (byte) 0x01, (byte) 0x00);
    }

    @Test
    void testEncodeUnsigned4Bytes() {
        // 65536 -> 0x1a 0x00 0x01 0x00 0x00
        assertThat(encoder.encode(65536)).containsExactly(
                (byte) 0x1a, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00);
    }

    @Test
    void testEncodeNegativeIntegers() {
        // -1 -> major type 1, value 0 -> 0x20
        assertThat(encoder.encode(-1)).containsExactly((byte) 0x20);
        // -10 -> major type 1, value 9 -> 0x29
        assertThat(encoder.encode(-10)).containsExactly((byte) 0x29);
        // -24 -> major type 1, value 23 -> 0x37
        assertThat(encoder.encode(-24)).containsExactly((byte) 0x37);
        // -25 -> major type 1, value 24 -> 0x38 0x18
        assertThat(encoder.encode(-25)).containsExactly((byte) 0x38, (byte) 0x18);
    }

    @Test
    void testEncodeFloat32() {
        var bytes = encoder.encode(1.5f);
        assertThat(bytes[0]).isEqualTo((byte) 0xfa);
        assertThat(bytes.length).isEqualTo(5);
    }

    @Test
    void testEncodeFloat64() {
        var bytes = encoder.encode(1.5);
        assertThat(bytes[0]).isEqualTo((byte) 0xfb);
        assertThat(bytes.length).isEqualTo(9);
    }

    @Test
    void testEncodeTextString() {
        // "abc" -> 0x63 0x61 0x62 0x63 (major type 3, length 3)
        assertThat(encoder.encode("abc")).containsExactly(
                (byte) 0x63, (byte) 0x61, (byte) 0x62, (byte) 0x63);
    }

    @Test
    void testEncodeEmptyString() {
        assertThat(encoder.encode("")).containsExactly((byte) 0x60);
    }

    @Test
    void testEncodeByteString() {
        var data = new byte[]{0x01, 0x02};
        var bytes = encoder.encode(data);
        // major type 2, length 2 -> 0x42
        assertThat(bytes[0]).isEqualTo((byte) 0x42);
        assertThat(bytes[1]).isEqualTo((byte) 0x01);
        assertThat(bytes[2]).isEqualTo((byte) 0x02);
    }

    @Test
    void testEncodeArray() {
        // [1, 2] -> 0x82 0x01 0x02 (major type 4, length 2)
        assertThat(encoder.encode(List.of(1, 2))).containsExactly(
                (byte) 0x82, (byte) 0x01, (byte) 0x02);
    }

    @Test
    void testEncodeEmptyArray() {
        assertThat(encoder.encode(List.of())).containsExactly((byte) 0x80);
    }

    @Test
    void testEncodeMap() {
        // {"a": 1} -> 0xa1 0x61 0x61 0x01 (major type 5, length 1)
        var bytes = encoder.encode(Map.of("a", 1));
        assertThat(bytes[0]).isEqualTo((byte) 0xa1);
    }

    @Test
    void testEncodeEmptyMap() {
        assertThat(encoder.encode(Map.of())).containsExactly((byte) 0xa0);
    }
}
