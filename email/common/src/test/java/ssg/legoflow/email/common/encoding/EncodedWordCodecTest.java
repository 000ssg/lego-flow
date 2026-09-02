package ssg.legoflow.email.common.encoding;

import org.junit.jupiter.api.Test;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link EncodedWordCodec}.
 */
class EncodedWordCodecTest {

    @Test
    void testEncodeBase64Utf8() {
        String encoded = EncodedWordCodec.encodeBase64("Héllo", StandardCharsets.UTF_8);
        assertThat(encoded).startsWith("=?UTF-8?B?");
        assertThat(encoded).endsWith("?=");
    }

    @Test
    void testDecodeBase64Utf8() {
        String encoded = EncodedWordCodec.encodeBase64("Héllo", StandardCharsets.UTF_8);
        String decoded = EncodedWordCodec.decode(encoded);
        assertThat(decoded).isEqualTo("Héllo");
    }

    @Test
    void testEncodeQUtf8() {
        String encoded = EncodedWordCodec.encodeQ("Héllo", StandardCharsets.UTF_8);
        assertThat(encoded).startsWith("=?UTF-8?Q?");
        assertThat(encoded).endsWith("?=");
    }

    @Test
    void testDecodeQUtf8() {
        String encoded = EncodedWordCodec.encodeQ("Héllo", StandardCharsets.UTF_8);
        String decoded = EncodedWordCodec.decode(encoded);
        assertThat(decoded).isEqualTo("Héllo");
    }

    @Test
    void testDecodeQIso88591() {
        // =?ISO-8859-1?Q?=E9?= is é in ISO-8859-1
        String decoded = EncodedWordCodec.decode("=?ISO-8859-1?Q?=E9?=");
        assertThat(decoded).isEqualTo("é");
    }

    @Test
    void testDecodeBase64Iso88591() {
        // ISO-8859-1 encoding of "café"
        byte[] bytes = "café".getBytes(Charset.forName("ISO-8859-1"));
        String base64 = java.util.Base64.getEncoder().encodeToString(bytes);
        String encoded = "=?ISO-8859-1?B?" + base64 + "?=";
        String decoded = EncodedWordCodec.decode(encoded);
        assertThat(decoded).isEqualTo("café");
    }

    @Test
    void testDecodeQSpaceAsUnderscore() {
        // In Q encoding, underscore represents space
        String decoded = EncodedWordCodec.decode("=?UTF-8?Q?Hello_World?=");
        assertThat(decoded).isEqualTo("Hello World");
    }

    @Test
    void testDecodeNoEncodedWord() {
        // Plain text should pass through unchanged
        String result = EncodedWordCodec.decode("Hello, World!");
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    void testDecodeNullInput() {
        assertThat(EncodedWordCodec.decode(null)).isNull();
    }

    @Test
    void testDecodeAdjacentEncodedWords() {
        // Whitespace between adjacent encoded words should be ignored per RFC 2047
        String input = "=?UTF-8?B?SGVs?= =?UTF-8?B?bG8=?=";
        String decoded = EncodedWordCodec.decode(input);
        assertThat(decoded).isEqualTo("Hello");
    }

    @Test
    void testDecodeMixedEncodedAndPlain() {
        String input = "Re: =?UTF-8?B?SGVsbG8=?= World";
        String decoded = EncodedWordCodec.decode(input);
        assertThat(decoded).isEqualTo("Re: Hello World");
    }

    @Test
    void testEncodeDefaultUtf8Base64() {
        String encoded = EncodedWordCodec.encode("日本語");
        assertThat(encoded).startsWith("=?UTF-8?B?");
        String decoded = EncodedWordCodec.decode(encoded);
        assertThat(decoded).isEqualTo("日本語");
    }

    @Test
    void testNeedsEncodingAscii() {
        assertThat(EncodedWordCodec.needsEncoding("Hello, World!")).isFalse();
    }

    @Test
    void testNeedsEncodingNonAscii() {
        assertThat(EncodedWordCodec.needsEncoding("Héllo")).isTrue();
    }

    @Test
    void testQEncodingSpecialChars() {
        String encoded = EncodedWordCodec.encodeQ("a=b?c", StandardCharsets.UTF_8);
        assertThat(encoded).contains("=3D"); // encoded =
        assertThat(encoded).contains("=3F"); // encoded ?
        String decoded = EncodedWordCodec.decode(encoded);
        assertThat(decoded).isEqualTo("a=b?c");
    }

    @Test
    void testLowercaseEncodingSpecifier() {
        // Both lowercase b and q should work
        String decoded = EncodedWordCodec.decode("=?UTF-8?b?SGVsbG8=?=");
        assertThat(decoded).isEqualTo("Hello");
    }
}
