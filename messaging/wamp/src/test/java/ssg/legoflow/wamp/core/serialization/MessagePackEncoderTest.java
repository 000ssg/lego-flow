package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Byte-by-byte verification of MessagePack encoding against the specification.
 */
class MessagePackEncoderTest {

    private final MessagePackEncoder encoder = new MessagePackEncoder();

    @Test
    void testEncodeNil() {
        var bytes = encoder.encode(null);
        assertThat(bytes).containsExactly((byte) 0xc0);
    }

    @Test
    void testEncodeTrue() {
        var bytes = encoder.encode(true);
        assertThat(bytes).containsExactly((byte) 0xc3);
    }

    @Test
    void testEncodeFalse() {
        var bytes = encoder.encode(false);
        assertThat(bytes).containsExactly((byte) 0xc2);
    }

    @Test
    void testEncodePositiveFixint() {
        // 0 -> 0x00
        assertThat(encoder.encode(0)).containsExactly((byte) 0x00);
        // 1 -> 0x01
        assertThat(encoder.encode(1)).containsExactly((byte) 0x01);
        // 127 -> 0x7f
        assertThat(encoder.encode(127)).containsExactly((byte) 0x7f);
    }

    @Test
    void testEncodeNegativeFixint() {
        // -1 -> 0xff
        assertThat(encoder.encode(-1)).containsExactly((byte) 0xff);
        // -32 -> 0xe0
        assertThat(encoder.encode(-32)).containsExactly((byte) 0xe0);
    }

    @Test
    void testEncodeUint8() {
        // 128 -> 0xcc 0x80
        assertThat(encoder.encode(128)).containsExactly((byte) 0xcc, (byte) 0x80);
        // 255 -> 0xcc 0xff
        assertThat(encoder.encode(255)).containsExactly((byte) 0xcc, (byte) 0xff);
    }

    @Test
    void testEncodeUint16() {
        // 256 -> 0xcd 0x01 0x00
        assertThat(encoder.encode(256)).containsExactly((byte) 0xcd, (byte) 0x01, (byte) 0x00);
        // 65535 -> 0xcd 0xff 0xff
        assertThat(encoder.encode(65535)).containsExactly((byte) 0xcd, (byte) 0xff, (byte) 0xff);
    }

    @Test
    void testEncodeUint32() {
        // 65536 -> 0xce 0x00 0x01 0x00 0x00
        assertThat(encoder.encode(65536)).containsExactly(
                (byte) 0xce, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00);
    }

    @Test
    void testEncodeUint64() {
        // Value > 2^32 -> 0xcf followed by 8 bytes
        long val = 0x1_0000_0000L;
        var bytes = encoder.encode(val);
        assertThat(bytes[0]).isEqualTo((byte) 0xcf);
        assertThat(bytes.length).isEqualTo(9);
    }

    @Test
    void testEncodeInt8() {
        // -33 -> 0xd0 0xdf
        assertThat(encoder.encode(-33)).containsExactly((byte) 0xd0, (byte) 0xdf);
        // -128 -> 0xd0 0x80
        assertThat(encoder.encode(-128)).containsExactly((byte) 0xd0, (byte) 0x80);
    }

    @Test
    void testEncodeInt16() {
        // -129 -> 0xd1 0xff 0x7f
        assertThat(encoder.encode(-129)).containsExactly((byte) 0xd1, (byte) 0xff, (byte) 0x7f);
    }

    @Test
    void testEncodeInt32() {
        // -32769 -> 0xd2 ...
        var bytes = encoder.encode(-32769);
        assertThat(bytes[0]).isEqualTo((byte) 0xd2);
        assertThat(bytes.length).isEqualTo(5);
    }

    @Test
    void testEncodeInt64() {
        long val = -2_200_000_000L;
        var bytes = encoder.encode(val);
        assertThat(bytes[0]).isEqualTo((byte) 0xd3);
        assertThat(bytes.length).isEqualTo(9);
    }

    @Test
    void testEncodeFloat32() {
        var bytes = encoder.encode(1.5f);
        assertThat(bytes[0]).isEqualTo((byte) 0xca);
        assertThat(bytes.length).isEqualTo(5);
        // IEEE 754: 1.5f = 0x3fc00000
        assertThat(bytes[1]).isEqualTo((byte) 0x3f);
        assertThat(bytes[2]).isEqualTo((byte) 0xc0);
        assertThat(bytes[3]).isEqualTo((byte) 0x00);
        assertThat(bytes[4]).isEqualTo((byte) 0x00);
    }

    @Test
    void testEncodeFloat64() {
        var bytes = encoder.encode(1.5);
        assertThat(bytes[0]).isEqualTo((byte) 0xcb);
        assertThat(bytes.length).isEqualTo(9);
    }

    @Test
    void testEncodeFixstr() {
        // "abc" -> 0xa3 0x61 0x62 0x63
        assertThat(encoder.encode("abc")).containsExactly(
                (byte) 0xa3, (byte) 0x61, (byte) 0x62, (byte) 0x63);
        // empty string -> 0xa0
        assertThat(encoder.encode("")).containsExactly((byte) 0xa0);
    }

    @Test
    void testEncodeStr8() {
        var str = "a".repeat(32);
        var bytes = encoder.encode(str);
        assertThat(bytes[0]).isEqualTo((byte) 0xd9);
        assertThat(bytes[1]).isEqualTo((byte) 32);
    }

    @Test
    void testEncodeBin8() {
        var data = new byte[]{0x01, 0x02, 0x03};
        var bytes = encoder.encode(data);
        assertThat(bytes[0]).isEqualTo((byte) 0xc4);
        assertThat(bytes[1]).isEqualTo((byte) 3);
        assertThat(bytes[2]).isEqualTo((byte) 0x01);
        assertThat(bytes[3]).isEqualTo((byte) 0x02);
        assertThat(bytes[4]).isEqualTo((byte) 0x03);
    }

    @Test
    void testEncodeFixarray() {
        // [1, 2] -> 0x92 0x01 0x02
        var bytes = encoder.encode(List.of(1, 2));
        assertThat(bytes).containsExactly((byte) 0x92, (byte) 0x01, (byte) 0x02);
    }

    @Test
    void testEncodeEmptyArray() {
        assertThat(encoder.encode(List.of())).containsExactly((byte) 0x90);
    }

    @Test
    void testEncodeFixmap() {
        // {"a": 1} -> 0x81 0xa1 0x61 0x01
        var bytes = encoder.encode(Map.of("a", 1));
        assertThat(bytes).containsExactly(
                (byte) 0x81, (byte) 0xa1, (byte) 0x61, (byte) 0x01);
    }

    @Test
    void testEncodeEmptyMap() {
        assertThat(encoder.encode(Map.of())).containsExactly((byte) 0x80);
    }

    @Test
    void testEncodeNestedStructure() {
        // [1, "hello", [true, false]]
        var data = List.of(1, "hello", List.of(true, false));
        var bytes = encoder.encode(data);
        // Should start with fixarray(3) = 0x93
        assertThat(bytes[0]).isEqualTo((byte) 0x93);
    }
}
