package ssg.legoflow.http.transfer;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class CloseDelimitedCodecTest {

    @Test
    void testAccumulatesSingleChunk() {
        // Given
        var codec = new CloseDelimitedCodec();
        var data = ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8));

        // When
        codec.doFilter(null, data);
        ByteBuffer result = codec.finishBody();

        // Then
        assertThat(new String(toBytes(result), StandardCharsets.UTF_8)).isEqualTo("Hello");
    }

    @Test
    void testAccumulatesMultipleChunks() {
        // Given
        var codec = new CloseDelimitedCodec();

        // When
        codec.doFilter(null, ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8)));
        codec.doFilter(null, ByteBuffer.wrap(", ".getBytes(StandardCharsets.UTF_8)));
        codec.doFilter(null, ByteBuffer.wrap("World!".getBytes(StandardCharsets.UTF_8)));
        ByteBuffer result = codec.finishBody();

        // Then
        assertThat(new String(toBytes(result), StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    void testConnectionClosedFlag() {
        // Given
        var codec = new CloseDelimitedCodec();

        // Then
        assertThat(codec.isConnectionClosed()).isFalse();

        // When
        codec.finishBody();

        // Then
        assertThat(codec.isConnectionClosed()).isTrue();
    }

    @Test
    void testAccumulatedSize() {
        // Given
        var codec = new CloseDelimitedCodec();

        // When
        codec.doFilter(null, ByteBuffer.wrap(new byte[100]));
        codec.doFilter(null, ByteBuffer.wrap(new byte[50]));

        // Then
        assertThat(codec.getAccumulatedSize()).isEqualTo(150);
    }

    @Test
    void testEmptyBodyOnFinish() {
        // Given
        var codec = new CloseDelimitedCodec();

        // When
        ByteBuffer result = codec.finishBody();

        // Then
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void testDoFilterReturnsEmptyBeforeClose() {
        // Given
        var codec = new CloseDelimitedCodec();

        // When
        ByteBuffer[] result = codec.doFilter(null,
                ByteBuffer.wrap("data".getBytes(StandardCharsets.UTF_8)));

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void testDetermineFramingChunked() {
        assertThat(CloseDelimitedCodec.determineFraming("chunked", null))
                .isEqualTo(CloseDelimitedCodec.BodyFraming.CHUNKED);
        assertThat(CloseDelimitedCodec.determineFraming("Transfer-Encoding: chunked", "100"))
                .isEqualTo(CloseDelimitedCodec.BodyFraming.CHUNKED);
    }

    @Test
    void testDetermineFramingContentLength() {
        assertThat(CloseDelimitedCodec.determineFraming(null, "1024"))
                .isEqualTo(CloseDelimitedCodec.BodyFraming.CONTENT_LENGTH);
    }

    @Test
    void testDetermineFramingCloseDelimited() {
        assertThat(CloseDelimitedCodec.determineFraming(null, null))
                .isEqualTo(CloseDelimitedCodec.BodyFraming.CLOSE_DELIMITED);
    }

    @Test
    void testBinaryDataAccumulation() {
        // Given
        var codec = new CloseDelimitedCodec();
        byte[] binaryData = new byte[]{0x00, 0x01, (byte) 0xFF, (byte) 0xFE};

        // When
        codec.doFilter(null, ByteBuffer.wrap(binaryData));
        ByteBuffer result = codec.finishBody();

        // Then
        byte[] resultBytes = toBytes(result);
        assertThat(resultBytes).isEqualTo(binaryData);
    }

    private byte[] toBytes(ByteBuffer buf) {
        var dup = buf.duplicate();
        var bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return bytes;
    }
}
