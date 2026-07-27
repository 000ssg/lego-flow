package ssg.legoflow.http.transfer;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.header.ContentEncoding;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ContentEncodingCodecTest {

    private final Context ctx = new DefaultContext();

    @Test
    void testGzipCompressDecompressRoundtrip() {
        // Given
        var compressor = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.COMPRESS);
        var decompressor = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.DECOMPRESS);
        ByteBuffer original = ByteBuffer.wrap("Hello, Gzip World!".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] compressed = compressor.filter(ctx, original);
        ByteBuffer[] decompressed = decompressor.filter(ctx, compressed);

        // Then
        assertThat(decompressed.length).isEqualTo(1);
        assertThat(bufToString(decompressed[0])).isEqualTo("Hello, Gzip World!");
    }

    @Test
    void testDeflateCompressDecompressRoundtrip() {
        // Given
        var compressor = new ContentEncodingCodec(ContentEncoding.DEFLATE, ContentEncodingCodec.Mode.COMPRESS);
        var decompressor = new ContentEncodingCodec(ContentEncoding.DEFLATE, ContentEncodingCodec.Mode.DECOMPRESS);
        ByteBuffer original = ByteBuffer.wrap("Hello, Deflate World!".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] compressed = compressor.filter(ctx, original);
        ByteBuffer[] decompressed = decompressor.filter(ctx, compressed);

        // Then
        assertThat(decompressed.length).isEqualTo(1);
        assertThat(bufToString(decompressed[0])).isEqualTo("Hello, Deflate World!");
    }

    @Test
    void testGzipCompressedSmallerThanOriginalForLargeData() {
        // Given
        var compressor = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.COMPRESS);
        ByteBuffer original = ByteBuffer.wrap("a]".repeat(1000).getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] compressed = compressor.filter(ctx, original);

        // Then
        assertThat(compressed[0].remaining()).isLessThan(original.remaining());
    }

    @Test
    void testIdentityEncodingReturnsOriginal() {
        // Given
        var compressor = new ContentEncodingCodec(ContentEncoding.IDENTITY, ContentEncodingCodec.Mode.COMPRESS);
        ByteBuffer original = ByteBuffer.wrap("unchanged data".getBytes(StandardCharsets.UTF_8));

        // When
        ByteBuffer[] result = compressor.filter(ctx, original);

        // Then
        assertThat(result[0]).isEqualTo(original);
    }

    @Test
    void testGetEncoding() {
        // Given
        var codec = new ContentEncodingCodec(ContentEncoding.GZIP, ContentEncodingCodec.Mode.COMPRESS);

        // Then
        assertThat(codec.getEncoding()).isEqualTo(ContentEncoding.GZIP);
    }

    private static String bufToString(ByteBuffer buf) {
        var dup = buf.duplicate();
        var bytes = new byte[dup.remaining()];
        dup.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
