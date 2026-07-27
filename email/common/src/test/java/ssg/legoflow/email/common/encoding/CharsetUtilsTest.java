package ssg.legoflow.email.common.encoding;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CharsetUtils}.
 */
class CharsetUtilsTest {

    @Test
    void testForNameUtf8Variants() {
        assertThat(CharsetUtils.forName("UTF-8")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(CharsetUtils.forName("utf-8")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(CharsetUtils.forName("utf8")).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testForNameAscii() {
        assertThat(CharsetUtils.forName("US-ASCII")).isEqualTo(StandardCharsets.US_ASCII);
        assertThat(CharsetUtils.forName("ascii")).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testForNameIso88591Aliases() {
        assertThat(CharsetUtils.forName("ISO-8859-1")).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(CharsetUtils.forName("latin1")).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(CharsetUtils.forName("latin-1")).isEqualTo(StandardCharsets.ISO_8859_1);
        assertThat(CharsetUtils.forName("iso8859-1")).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void testForNameWindows1252() {
        Charset cp1252 = Charset.forName("windows-1252");
        assertThat(CharsetUtils.forName("windows-1252")).isEqualTo(cp1252);
        assertThat(CharsetUtils.forName("cp1252")).isEqualTo(cp1252);
    }

    @Test
    void testForNameNullOrBlankDefaultsToAscii() {
        assertThat(CharsetUtils.forName(null)).isEqualTo(StandardCharsets.US_ASCII);
        assertThat(CharsetUtils.forName("")).isEqualTo(StandardCharsets.US_ASCII);
        assertThat(CharsetUtils.forName("  ")).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testForNameUnknownThrows() {
        assertThatThrownBy(() -> CharsetUtils.forName("NONEXISTENT"))
                .isInstanceOf(UnsupportedCharsetException.class);
    }

    @Test
    void testDetectBomUtf8() {
        byte[] data = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'H', 'i'};
        assertThat(CharsetUtils.detectBom(data)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testDetectBomUtf16BE() {
        byte[] data = {(byte) 0xFE, (byte) 0xFF, 0, 'H'};
        assertThat(CharsetUtils.detectBom(data)).isEqualTo(StandardCharsets.UTF_16BE);
    }

    @Test
    void testDetectBomUtf16LE() {
        byte[] data = {(byte) 0xFF, (byte) 0xFE, 'H', 0};
        assertThat(CharsetUtils.detectBom(data)).isEqualTo(StandardCharsets.UTF_16LE);
    }

    @Test
    void testDetectBomNone() {
        assertThat(CharsetUtils.detectBom("Hello".getBytes(StandardCharsets.UTF_8))).isNull();
        assertThat(CharsetUtils.detectBom(null)).isNull();
        assertThat(CharsetUtils.detectBom(new byte[]{1})).isNull();
    }

    @Test
    void testIsValidUtf8() {
        assertThat(CharsetUtils.isValidUtf8("Hello".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(CharsetUtils.isValidUtf8("Héllo".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(CharsetUtils.isValidUtf8("日本語".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void testIsValidUtf8Invalid() {
        // Invalid sequence: 0xFF is never valid in UTF-8
        assertThat(CharsetUtils.isValidUtf8(new byte[]{(byte) 0xFF})).isFalse();
        // Truncated multi-byte
        assertThat(CharsetUtils.isValidUtf8(new byte[]{(byte) 0xC3})).isFalse();
    }

    @Test
    void testIsAscii() {
        assertThat(CharsetUtils.isAscii("Hello".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(CharsetUtils.isAscii(new byte[]{(byte) 0x80})).isFalse();
    }

    @Test
    void testDetectAscii() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.US_ASCII);
        assertThat(CharsetUtils.detect(data)).isEqualTo(StandardCharsets.US_ASCII);
    }

    @Test
    void testDetectUtf8() {
        byte[] data = "Héllo".getBytes(StandardCharsets.UTF_8);
        assertThat(CharsetUtils.detect(data)).isEqualTo(StandardCharsets.UTF_8);
    }

    @Test
    void testDetectFallbackIso88591() {
        // Pure ISO-8859-1 bytes that aren't valid UTF-8
        byte[] data = {(byte) 0xE9, (byte) 0xE8, (byte) 0xEA}; // é, è, ê in ISO-8859-1
        // These aren't valid UTF-8 as isolated bytes
        // Actually 0xE9 starts a 3-byte sequence in UTF-8 but 0xE8 would need continuation
        assertThat(CharsetUtils.detect(data)).isEqualTo(StandardCharsets.ISO_8859_1);
    }

    @Test
    void testToUtf8SameCharset() {
        byte[] data = "Hello".getBytes(StandardCharsets.UTF_8);
        assertThat(CharsetUtils.toUtf8(data, StandardCharsets.UTF_8)).isSameAs(data);
    }

    @Test
    void testToUtf8FromIso88591() {
        byte[] isoData = "café".getBytes(StandardCharsets.ISO_8859_1);
        byte[] utf8Data = CharsetUtils.toUtf8(isoData, StandardCharsets.ISO_8859_1);
        assertThat(new String(utf8Data, StandardCharsets.UTF_8)).isEqualTo("café");
    }
}
