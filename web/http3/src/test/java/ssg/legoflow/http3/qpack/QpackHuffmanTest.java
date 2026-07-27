package ssg.legoflow.http3.qpack;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class QpackHuffmanTest {

    @Test
    void testEncodeDecodeRoundtripSimple() {
        // Given
        var input = "www.example.com";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodeDecodeRoundtripEmpty() {
        // Given
        var input = "";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEmpty();
    }

    @Test
    void testEncodeDecodeRoundtripHeaders() {
        // Given
        var input = "content-type";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodeDecodeRoundtripPath() {
        // Given
        var input = "/api/v1/resource";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodeDecodeRoundtripStatusValue() {
        // Given
        var input = "200";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodeDecodeRoundtripMimeType() {
        // Given
        var input = "application/json";

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodeProducesShorterOutput() {
        // Given
        var input = "www.example.com";
        var rawBytes = input.getBytes();

        // When
        var encoded = QpackHuffman.encode(input);

        // Then: Huffman encoding should be shorter for typical ASCII strings
        assertThat(encoded.length).isLessThan(rawBytes.length);
    }

    @Test
    void testDecodeWithOffsetAndLength() {
        // Given
        var input = "hello";
        var encoded = QpackHuffman.encode(input);
        // Wrap encoded in a larger array with offset
        var padded = new byte[encoded.length + 4];
        System.arraycopy(encoded, 0, padded, 2, encoded.length);

        // When
        var decoded = QpackHuffman.decode(padded, 2, encoded.length);

        // Then
        assertThat(decoded).isEqualTo(input);
    }

    @Test
    void testEncodedLength() {
        // Given
        var input = "www.example.com".getBytes();

        // When
        int encodedLen = QpackHuffman.encodedLength(input);
        var encoded = QpackHuffman.encode(input);

        // Then
        assertThat(encodedLen).isEqualTo(encoded.length);
    }

    @Test
    void testEncodeDecodeAllPrintableAscii() {
        // Given: all printable ASCII characters
        var sb = new StringBuilder();
        for (char c = 32; c < 127; c++) {
            sb.append(c);
        }
        var input = sb.toString();

        // When
        var encoded = QpackHuffman.encode(input);
        var decoded = QpackHuffman.decode(encoded);

        // Then
        assertThat(decoded).isEqualTo(input);
    }
}
