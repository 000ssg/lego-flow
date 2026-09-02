package ssg.legoflow.email.common.encoding;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link QuotedPrintableCodec}.
 */
class QuotedPrintableCodecTest {

    @Test
    void testEncodeAsciiPassthrough() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        assertThat(encoded).isEqualTo("Hello, World!");
    }

    @Test
    void testDecodeAsciiPassthrough() {
        byte[] decoded = QuotedPrintableCodec.decode("Hello, World!");
        assertThat(new String(decoded, StandardCharsets.US_ASCII)).isEqualTo("Hello, World!");
    }

    @Test
    void testEncodeNonAscii() {
        byte[] data = {(byte) 0xE9}; // é in ISO-8859-1
        String encoded = QuotedPrintableCodec.encode(data);
        assertThat(encoded).isEqualTo("=E9");
    }

    @Test
    void testDecodeHexSequence() {
        byte[] decoded = QuotedPrintableCodec.decode("=E9");
        assertThat(decoded).isEqualTo(new byte[]{(byte) 0xE9});
    }

    @Test
    void testEncodeEqualsSign() {
        byte[] data = "a=b".getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        assertThat(encoded).isEqualTo("a=3Db");
    }

    @Test
    void testSoftLineBreak() {
        // Create a string long enough to require soft line break
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            sb.append('A');
        }
        byte[] data = sb.toString().getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        assertThat(encoded).contains("=\r\n");
        // Each line should be <=76 chars
        for (String line : encoded.split("\r\n")) {
            assertThat(line.length()).isLessThanOrEqualTo(76);
        }
    }

    @Test
    void testDecodeSoftLineBreak() {
        byte[] decoded = QuotedPrintableCodec.decode("Hello=\r\n World");
        assertThat(new String(decoded, StandardCharsets.US_ASCII)).isEqualTo("Hello World");
    }

    @Test
    void testCrlfPassthrough() {
        byte[] data = "Line1\r\nLine2".getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        assertThat(encoded).isEqualTo("Line1\r\nLine2");
    }

    @Test
    void testRoundTrip() {
        String original = "Hello = World! Non-ASCII: ";
        // Add some high bytes
        byte[] data = new byte[original.length() + 3];
        System.arraycopy(original.getBytes(StandardCharsets.US_ASCII), 0, data, 0, original.length());
        data[original.length()] = (byte) 0xC3;
        data[original.length() + 1] = (byte) 0xA9;
        data[original.length() + 2] = (byte) 0xFF;

        String encoded = QuotedPrintableCodec.encode(data);
        byte[] decoded = QuotedPrintableCodec.decode(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testTabPreserved() {
        byte[] data = "Col1\tCol2\tCol3\r\n".getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        // Tab is allowed in QP (it's printable)
        // But trailing tab before CRLF should be encoded
        byte[] decoded = QuotedPrintableCodec.decode(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void testTrailingSpaceEncoded() {
        byte[] data = "Hello   \r\n".getBytes(StandardCharsets.US_ASCII);
        String encoded = QuotedPrintableCodec.encode(data);
        // Trailing spaces before CRLF must be encoded
        assertThat(encoded).contains("=20");
    }

    @Test
    void testDecodeBareLF() {
        // Lenient: =\n treated as soft line break
        byte[] decoded = QuotedPrintableCodec.decode("Hello=\n World");
        assertThat(new String(decoded, StandardCharsets.US_ASCII)).isEqualTo("Hello World");
    }

    @Test
    void testDecodeInvalidHexThrows() {
        assertThatThrownBy(() -> QuotedPrintableCodec.decode("=GG"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDecodeTruncatedThrows() {
        assertThatThrownBy(() -> QuotedPrintableCodec.decode("=A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEncodeWithCharset() {
        String text = "Hello";
        String encoded = QuotedPrintableCodec.encode(text, StandardCharsets.UTF_8);
        assertThat(encoded).isEqualTo("Hello");
    }
}
