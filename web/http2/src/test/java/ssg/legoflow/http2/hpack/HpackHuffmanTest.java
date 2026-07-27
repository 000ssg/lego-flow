package ssg.legoflow.http2.hpack;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class HpackHuffmanTest {

    @Test
    void testEncodeDecodeRoundTrip() {
        String original = "www.example.com";
        byte[] raw = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = HpackHuffman.encode(raw);
        byte[] decoded = HpackHuffman.decode(encoded);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void testEncodeProducesSmallerOutput() {
        String input = "www.example.com";
        byte[] raw = input.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = HpackHuffman.encode(raw);

        assertThat(encoded.length).isLessThan(raw.length);
    }

    @Test
    void testEncodeDecodeEmptyString() {
        byte[] encoded = HpackHuffman.encode(new byte[0]);
        byte[] decoded = HpackHuffman.decode(encoded);

        assertThat(decoded).isEmpty();
    }

    @Test
    void testEncodeDecodeSingleCharacter() {
        byte[] raw = "a".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = HpackHuffman.encode(raw);
        byte[] decoded = HpackHuffman.decode(encoded);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("a");
    }

    @Test
    void testEncodeDecodeAllPrintableAscii() {
        StringBuilder sb = new StringBuilder();
        for (int i = 32; i < 127; i++) {
            sb.append((char) i);
        }
        String original = sb.toString();
        byte[] raw = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = HpackHuffman.encode(raw);
        byte[] decoded = HpackHuffman.decode(encoded);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }

    @Test
    void testEncodeDecodeHeaderValues() {
        String[] values = {
            "text/html",
            "application/json",
            "gzip, deflate",
            "/index.html",
            "200",
            "GET",
            "POST"
        };

        for (String value : values) {
            byte[] raw = value.getBytes(StandardCharsets.UTF_8);
            byte[] encoded = HpackHuffman.encode(raw);
            byte[] decoded = HpackHuffman.decode(encoded);
            assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(value);
        }
    }

    @Test
    void testEncodedLength() {
        String input = "www.example.com";
        byte[] raw = input.getBytes(StandardCharsets.UTF_8);
        int length = HpackHuffman.encodedLength(raw);
        byte[] encoded = HpackHuffman.encode(raw);

        assertThat(length).isEqualTo(encoded.length);
    }

    @Test
    void testEncodeDecodeNumbers() {
        String original = "1234567890";
        byte[] raw = original.getBytes(StandardCharsets.UTF_8);
        byte[] encoded = HpackHuffman.encode(raw);
        byte[] decoded = HpackHuffman.decode(encoded);

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo(original);
    }
}
