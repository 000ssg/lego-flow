package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class ChunkedCodecTest {

    private final Context ctx = new DefaultContext();

    @Test
    void testEncodeDecodeRoundtrip() {
        // Given
        var encoder = new ChunkedCodec(ChunkedCodec.Mode.ENCODE);
        var decoder = new ChunkedCodec(ChunkedCodec.Mode.DECODE);
        ByteBuffer original = ByteBuffer.wrap("Hello, World!".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] encoded = encoder.filter(ctx, original);
        ByteBuffer[] decoded = decoder.filter(ctx, encoded);

        // Then
        assertThat(decoded.length).isEqualTo(1);
        assertThat(bufToString(decoded[0])).isEqualTo("Hello, World!");
    }

    @Test
    void testEncodeMultipleChunks() {
        // Given
        var encoder = new ChunkedCodec(ChunkedCodec.Mode.ENCODE);
        ByteBuffer chunk1 = ByteBuffer.wrap("Hello".getBytes(StandardCharsets.UTF_8));
        ByteBuffer chunk2 = ByteBuffer.wrap("World".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] encoded = encoder.filter(ctx, chunk1, chunk2);

        // Then
        assertThat(encoded.length).isEqualTo(2);
        String enc1 = bufToString(encoded[0], StandardCharsets.US_ASCII);
        assertThat(enc1).startsWith("5\r\n");
    }

    @Test
    void testDecodeMultipleChunks() {
        // Given
        var encoder = new ChunkedCodec(ChunkedCodec.Mode.ENCODE);
        var decoder = new ChunkedCodec(ChunkedCodec.Mode.DECODE);
        ByteBuffer chunk1 = ByteBuffer.wrap("abc".getBytes(StandardCharsets.UTF_8));
        ByteBuffer chunk2 = ByteBuffer.wrap("defgh".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] encoded = encoder.filter(ctx, chunk1, chunk2);
        ByteBuffer[] decoded = decoder.filter(ctx, encoded);

        // Then
        assertThat(decoded.length).isEqualTo(2);
        assertThat(bufToString(decoded[0])).isEqualTo("abc");
        assertThat(bufToString(decoded[1])).isEqualTo("defgh");
    }

    @Test
    void testEncodeProducesCorrectFormat() {
        // Given
        var encoder = new ChunkedCodec(ChunkedCodec.Mode.ENCODE);
        ByteBuffer data = ByteBuffer.wrap("Hi".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] encoded = encoder.filter(ctx, data);

        // Then
        String result = bufToString(encoded[0], StandardCharsets.US_ASCII);
        assertThat(result).isEqualTo("2\r\nHi\r\n");
    }

    @Test
    void testDecodeZeroLengthChunkIgnored() {
        // Given
        var decoder = new ChunkedCodec(ChunkedCodec.Mode.DECODE);
        ByteBuffer zeroChunk = ByteBuffer.wrap("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));

        // When
        ByteBuffer[] decoded = decoder.filter(ctx, zeroChunk);

        // Then
        assertThat(decoded).isEmpty();
    }

    private static String bufToString(ByteBuffer buf) {
        return bufToString(buf, StandardCharsets.UTF_8);
    }

    private static String bufToString(ByteBuffer buf, java.nio.charset.Charset charset) {
        var dup = buf.duplicate();
        var bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return new String(bytes, charset);
    }
}
