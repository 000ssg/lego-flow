package ssg.legoflow.email.common.encoding;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link Base64Codec}.
 */
class Base64CodecTest {

    @Test
    void testEncodeEmptyInput() {
        assertThat(Base64Codec.encode(new byte[0])).isEmpty();
    }

    @Test
    void testEncodeSimpleString() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Codec.encode(data);
        assertThat(encoded).isEqualTo("SGVsbG8sIFdvcmxkIQ==");
    }

    @Test
    void testDecodeSimpleString() {
        byte[] decoded = Base64Codec.decode("SGVsbG8sIFdvcmxkIQ==");
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    void testRoundTrip() {
        byte[] original = "The quick brown fox jumps over the lazy dog.".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Codec.encode(original);
        byte[] decoded = Base64Codec.decode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testLineWrappingAt76Chars() {
        // 57 bytes produces exactly 76 Base64 characters (one full line)
        byte[] data = new byte[120]; // Will produce >76 chars
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 26 + 'A');
        }
        String encoded = Base64Codec.encode(data);
        String[] lines = encoded.split("\r\n");
        for (int i = 0; i < lines.length - 1; i++) {
            assertThat(lines[i].length()).isEqualTo(76);
        }
        // Last line may be shorter
        assertThat(lines[lines.length - 1].length()).isLessThanOrEqualTo(76);
    }

    @Test
    void testDecodeIgnoresWhitespace() {
        String encoded = "SGVs\r\nbG8s\nIFdv\t  cmxkIQ==";
        byte[] decoded = Base64Codec.decode(encoded);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    void testEncodePadding() {
        // 1 byte -> 4 chars with 2 padding
        assertThat(Base64Codec.encode(new byte[]{65})).isEqualTo("QQ==");
        // 2 bytes -> 4 chars with 1 padding
        assertThat(Base64Codec.encode(new byte[]{65, 66})).isEqualTo("QUI=");
        // 3 bytes -> 4 chars with no padding
        assertThat(Base64Codec.encode(new byte[]{65, 66, 67})).isEqualTo("QUJD");
    }

    @Test
    void testEncodeRawNoLineWrapping() {
        byte[] data = new byte[120];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 26 + 'A');
        }
        String encoded = Base64Codec.encodeRaw(data);
        assertThat(encoded).doesNotContain("\r\n");
    }

    @Test
    void testEncodeBinaryData() {
        byte[] data = new byte[256];
        for (int i = 0; i < 256; i++) {
            data[i] = (byte) i;
        }
        String encoded = Base64Codec.encode(data);
        byte[] decoded = Base64Codec.decode(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testEncodeToBytes() {
        byte[] data = "Test".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = Base64Codec.encodeToBytes(data);
        assertThat(new String(encoded, StandardCharsets.US_ASCII)).isEqualTo("VGVzdA==");
    }

    @Test
    void testDecodeBytes() {
        byte[] encoded = "VGVzdA==".getBytes(StandardCharsets.US_ASCII);
        byte[] decoded = Base64Codec.decodeBytes(encoded);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Test");
    }

    @Test
    void testDecodeInvalidThrows() {
        assertThatThrownBy(() -> Base64Codec.decode("!!!invalid!!!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testShortInputNoLineWrapping() {
        byte[] data = "Hi".getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Codec.encode(data);
        assertThat(encoded).doesNotContain("\r\n");
        assertThat(encoded).isEqualTo("SGk=");
    }

    @Test
    void testUtf8ContentRoundTrip() {
        String text = "Bonjour le monde! éèê üöä";
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        String encoded = Base64Codec.encode(data);
        byte[] decoded = Base64Codec.decode(encoded);
        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(text);
    }
}
