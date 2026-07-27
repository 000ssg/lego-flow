package ssg.legoflow.wamp.core.serialization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for MessagePack decoding, verifying correct interpretation of binary formats.
 */
class MessagePackDecoderTest {

    private final MessagePackDecoder decoder = new MessagePackDecoder();

    @Test
    void testDecodeNil() {
        assertThat(decoder.decode(new byte[]{(byte) 0xc0})).isNull();
    }

    @Test
    void testDecodeTrue() {
        assertThat(decoder.decode(new byte[]{(byte) 0xc3})).isEqualTo(true);
    }

    @Test
    void testDecodeFalse() {
        assertThat(decoder.decode(new byte[]{(byte) 0xc2})).isEqualTo(false);
    }

    @Test
    void testDecodePositiveFixint() {
        assertThat(decoder.decode(new byte[]{0x00})).isEqualTo(0);
        assertThat(decoder.decode(new byte[]{0x01})).isEqualTo(1);
        assertThat(decoder.decode(new byte[]{0x7f})).isEqualTo(127);
    }

    @Test
    void testDecodeNegativeFixint() {
        assertThat(decoder.decode(new byte[]{(byte) 0xff})).isEqualTo(-1);
        assertThat(decoder.decode(new byte[]{(byte) 0xe0})).isEqualTo(-32);
    }

    @Test
    void testDecodeUint8() {
        assertThat(decoder.decode(new byte[]{(byte) 0xcc, (byte) 0x80})).isEqualTo(128);
        assertThat(decoder.decode(new byte[]{(byte) 0xcc, (byte) 0xff})).isEqualTo(255);
    }

    @Test
    void testDecodeUint16() {
        assertThat(decoder.decode(new byte[]{(byte) 0xcd, (byte) 0x01, (byte) 0x00})).isEqualTo(256);
    }

    @Test
    void testDecodeUint32() {
        assertThat(decoder.decode(new byte[]{
                (byte) 0xce, (byte) 0x00, (byte) 0x01, (byte) 0x00, (byte) 0x00})).isEqualTo(65536);
    }

    @Test
    void testDecodeInt8() {
        assertThat(decoder.decode(new byte[]{(byte) 0xd0, (byte) 0xdf})).isEqualTo(-33);
        assertThat(decoder.decode(new byte[]{(byte) 0xd0, (byte) 0x80})).isEqualTo(-128);
    }

    @Test
    void testDecodeFloat32() {
        // 1.5f = 0x3fc00000
        var bytes = new byte[]{(byte) 0xca, (byte) 0x3f, (byte) 0xc0, (byte) 0x00, (byte) 0x00};
        assertThat(decoder.decode(bytes)).isEqualTo(1.5f);
    }

    @Test
    void testDecodeFloat64() {
        // 1.5 = 0x3ff8000000000000
        var bytes = new byte[]{(byte) 0xcb, (byte) 0x3f, (byte) 0xf8,
                (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        assertThat(decoder.decode(bytes)).isEqualTo(1.5);
    }

    @Test
    void testDecodeFixstr() {
        // "abc" = 0xa3 0x61 0x62 0x63
        assertThat(decoder.decode(new byte[]{(byte) 0xa3, 0x61, 0x62, 0x63})).isEqualTo("abc");
    }

    @Test
    void testDecodeEmptyString() {
        assertThat(decoder.decode(new byte[]{(byte) 0xa0})).isEqualTo("");
    }

    @Test
    void testDecodeBin8() {
        var bytes = new byte[]{(byte) 0xc4, (byte) 0x03, 0x01, 0x02, 0x03};
        var result = (byte[]) decoder.decode(bytes);
        assertThat(result).containsExactly((byte) 0x01, (byte) 0x02, (byte) 0x03);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDecodeFixarray() {
        // [1, 2] = 0x92 0x01 0x02
        var result = (List<Object>) decoder.decode(new byte[]{(byte) 0x92, 0x01, 0x02});
        assertThat(result).containsExactly(1, 2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDecodeEmptyArray() {
        var result = (List<Object>) decoder.decode(new byte[]{(byte) 0x90});
        assertThat(result).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDecodeFixmap() {
        // {"a": 1} = 0x81 0xa1 0x61 0x01
        var result = (Map<String, Object>) decoder.decode(
                new byte[]{(byte) 0x81, (byte) 0xa1, 0x61, 0x01});
        assertThat(result).containsEntry("a", 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void testDecodeEmptyMap() {
        var result = (Map<String, Object>) decoder.decode(new byte[]{(byte) 0x80});
        assertThat(result).isEmpty();
    }

    @Test
    void testDecodeInvalidFormatThrows() {
        assertThatThrownBy(() -> decoder.decode(new byte[]{(byte) 0xc1}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeEmptyDataThrows() {
        assertThatThrownBy(() -> decoder.decode(new byte[]{}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
