package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.*;

class GrpcFrameCodecTest {

    @Nested
    class EncodingTests {

        @Test
        void testEncodeUncompressed() {
            byte[] message = {0x01, 0x02, 0x03};
            byte[] framed = GrpcFrameCodec.encode(message);

            assertThat(framed).hasSize(8); // 5 header + 3 data
            assertThat(framed[0]).isEqualTo((byte) 0); // not compressed
            assertThat(framed[1]).isEqualTo((byte) 0);
            assertThat(framed[2]).isEqualTo((byte) 0);
            assertThat(framed[3]).isEqualTo((byte) 0);
            assertThat(framed[4]).isEqualTo((byte) 3); // length = 3
        }

        @Test
        void testEncodeCompressedFlag() {
            byte[] message = {0x01};
            byte[] framed = GrpcFrameCodec.encode(message, true);

            assertThat(framed[0]).isEqualTo((byte) 1); // compressed
        }

        @Test
        void testEncodeEmptyMessage() {
            byte[] framed = GrpcFrameCodec.encode(new byte[0]);

            assertThat(framed).hasSize(5);
            assertThat(framed[4]).isEqualTo((byte) 0);
        }

        @Test
        void testEncodeLargeMessage() {
            byte[] message = new byte[1000];
            byte[] framed = GrpcFrameCodec.encode(message);

            assertThat(framed).hasSize(1005);
            // Length = 1000 = 0x000003E8
            assertThat(framed[1]).isEqualTo((byte) 0x00);
            assertThat(framed[2]).isEqualTo((byte) 0x00);
            assertThat(framed[3]).isEqualTo((byte) 0x03);
            assertThat(framed[4]).isEqualTo((byte) 0xE8);
        }
    }

    @Nested
    class DecodingTests {

        @Test
        void testDecodeRoundTrip() {
            byte[] original = {0x0A, 0x0B, 0x0C, 0x0D};
            byte[] framed = GrpcFrameCodec.encode(original);

            var decoded = GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(framed));
            assertThat(decoded).isNotNull();
            assertThat(decoded.compressed()).isFalse();
            assertThat(decoded.data()).containsExactly(original);
        }

        @Test
        void testDecodeCompressedFlag() {
            byte[] framed = GrpcFrameCodec.encode(new byte[]{1, 2}, true);
            var decoded = GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(framed));

            assertThat(decoded).isNotNull();
            assertThat(decoded.compressed()).isTrue();
        }

        @Test
        void testDecodeInsufficientHeader() {
            var decoded = GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(new byte[3]));
            assertThat(decoded).isNull();
        }

        @Test
        void testDecodeInsufficientPayload() {
            byte[] partial = {0x00, 0x00, 0x00, 0x00, 0x10}; // says 16 bytes, but only header
            var decoded = GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(partial));
            assertThat(decoded).isNull();
        }

        @Test
        void testDecodeExceedsMaxSize() {
            byte[] framed = GrpcFrameCodec.encode(new byte[100]);
            assertThatThrownBy(() -> GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(framed), 50))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exceeds maximum");
        }

        @Test
        void testDecodeMultipleFrames() {
            byte[] frame1 = GrpcFrameCodec.encode(new byte[]{1, 2});
            byte[] frame2 = GrpcFrameCodec.encode(new byte[]{3, 4, 5});
            byte[] combined = new byte[frame1.length + frame2.length];
            System.arraycopy(frame1, 0, combined, 0, frame1.length);
            System.arraycopy(frame2, 0, combined, frame1.length, frame2.length);

            var frames = GrpcFrameCodec.decodeAllFrames(ByteBuffer.wrap(combined));
            assertThat(frames).hasSize(2);
            assertThat(frames.get(0).data()).containsExactly(1, 2);
            assertThat(frames.get(1).data()).containsExactly(3, 4, 5);
        }

        @Test
        void testDecodeEmptyBuffer() {
            var frames = GrpcFrameCodec.decodeAllFrames(ByteBuffer.allocate(0));
            assertThat(frames).isEmpty();
        }
    }

    @Nested
    class CompressionTests {

        @Test
        void testGzipRoundTrip() {
            byte[] original = "Hello, gRPC compression!".getBytes();
            byte[] compressed = GrpcFrameCodec.compress(original, GrpcEncoding.GZIP);
            byte[] decompressed = GrpcFrameCodec.decompress(compressed, GrpcEncoding.GZIP);
            assertThat(decompressed).containsExactly(original);
        }

        @Test
        void testDeflateRoundTrip() {
            byte[] original = "Hello, deflate compression!".getBytes();
            byte[] compressed = GrpcFrameCodec.compress(original, GrpcEncoding.DEFLATE);
            byte[] decompressed = GrpcFrameCodec.decompress(compressed, GrpcEncoding.DEFLATE);
            assertThat(decompressed).containsExactly(original);
        }

        @Test
        void testIdentityNoOp() {
            byte[] original = {1, 2, 3};
            byte[] result = GrpcFrameCodec.compress(original, GrpcEncoding.IDENTITY);
            assertThat(result).isSameAs(original);
        }

        @Test
        void testEncodeCompressedGzip() {
            byte[] original = "test data for compression".getBytes();
            byte[] framed = GrpcFrameCodec.encodeCompressed(original, GrpcEncoding.GZIP);

            var decoded = GrpcFrameCodec.decodeFrame(ByteBuffer.wrap(framed));
            assertThat(decoded).isNotNull();
            assertThat(decoded.compressed()).isTrue();

            byte[] decompressed = GrpcFrameCodec.decompressIfNeeded(decoded, GrpcEncoding.GZIP);
            assertThat(decompressed).containsExactly(original);
        }

        @Test
        void testDecompressIfNeededNotCompressed() {
            var frame = new GrpcFrameCodec.DecodedFrame(false, new byte[]{1, 2, 3});
            byte[] result = GrpcFrameCodec.decompressIfNeeded(frame, GrpcEncoding.GZIP);
            assertThat(result).containsExactly(1, 2, 3);
        }

        @Test
        void testGzipLargeData() {
            byte[] original = new byte[10000];
            for (int i = 0; i < original.length; i++) {
                original[i] = (byte) (i % 256);
            }
            byte[] compressed = GrpcFrameCodec.compress(original, GrpcEncoding.GZIP);
            byte[] decompressed = GrpcFrameCodec.decompress(compressed, GrpcEncoding.GZIP);
            assertThat(decompressed).containsExactly(original);
        }
    }
}
