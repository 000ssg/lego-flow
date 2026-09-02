package ssg.legoflow.media.rtsp.interleaved;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link InterleavedFrameCodec}.
 */
class InterleavedFrameCodecTest {

    @Test
    void testEncodeFrame() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        var frame = new InterleavedFrame(0, data);
        byte[] encoded = InterleavedFrameCodec.encode(frame);

        assertThat(encoded.length).isEqualTo(8); // 4 header + 4 data
        assertThat(encoded[0]).isEqualTo((byte) '$');
        assertThat(encoded[1]).isEqualTo((byte) 0);
        assertThat((encoded[2] << 8) | (encoded[3] & 0xFF)).isEqualTo(4); // length
        assertThat(encoded[4]).isEqualTo((byte) 0x01);
    }

    @Test
    void testEncodeChannel255() {
        var frame = new InterleavedFrame(255, new byte[]{0x42});
        byte[] encoded = InterleavedFrameCodec.encode(frame);
        assertThat(encoded[1] & 0xFF).isEqualTo(255);
    }

    @Test
    void testDecodeFromByteBuffer() {
        byte[] data = {0x01, 0x02, 0x03};
        var frame = new InterleavedFrame(2, data);
        byte[] encoded = InterleavedFrameCodec.encode(frame);

        var buffer = ByteBuffer.wrap(encoded);
        var decoded = InterleavedFrameCodec.decode(buffer);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().channel()).isEqualTo(2);
        assertThat(decoded.get().data()).isEqualTo(data);
    }

    @Test
    void testDecodeInsufficientHeader() {
        var buffer = ByteBuffer.wrap(new byte[]{'$', 0x00});
        var decoded = InterleavedFrameCodec.decode(buffer);
        assertThat(decoded).isEmpty();
    }

    @Test
    void testDecodeInsufficientPayload() {
        byte[] partial = {'$', 0x00, 0x00, 0x0A, 0x01, 0x02}; // length=10 but only 2 bytes
        var buffer = ByteBuffer.wrap(partial);
        var decoded = InterleavedFrameCodec.decode(buffer);
        assertThat(decoded).isEmpty();
    }

    @Test
    void testDecodeWrongMagicThrows() {
        var buffer = ByteBuffer.wrap(new byte[]{'R', 0x00, 0x00, 0x00});
        assertThatThrownBy(() -> InterleavedFrameCodec.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0x52");
    }

    @Test
    void testDecodeFromByteArray() {
        byte[] data = {0x10, 0x20, 0x30};
        var frame = new InterleavedFrame(1, data);
        byte[] encoded = InterleavedFrameCodec.encode(frame);

        var decoded = InterleavedFrameCodec.decode(encoded, 0);
        assertThat(decoded.channel()).isEqualTo(1);
        assertThat(decoded.data()).isEqualTo(data);
    }

    @Test
    void testDecodeFromByteArrayOffset() {
        byte[] data = {0x10, 0x20};
        var frame = new InterleavedFrame(3, data);
        byte[] encoded = InterleavedFrameCodec.encode(frame);
        byte[] padded = new byte[5 + encoded.length];
        System.arraycopy(encoded, 0, padded, 5, encoded.length);

        var decoded = InterleavedFrameCodec.decode(padded, 5);
        assertThat(decoded.channel()).isEqualTo(3);
        assertThat(decoded.data()).isEqualTo(data);
    }

    @Test
    void testRoundTrip() {
        byte[] rtpPacket = new byte[172]; // Typical RTP packet size
        for (int i = 0; i < rtpPacket.length; i++) rtpPacket[i] = (byte) (i & 0xFF);
        var original = new InterleavedFrame(0, rtpPacket);

        byte[] encoded = InterleavedFrameCodec.encode(original);
        var decoded = InterleavedFrameCodec.decode(encoded, 0);

        assertThat(decoded.channel()).isEqualTo(original.channel());
        assertThat(decoded.data()).isEqualTo(rtpPacket);
    }

    @Test
    void testRoundTripByteBuffer() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        var original = new InterleavedFrame(4, data);

        byte[] encoded = InterleavedFrameCodec.encode(original);
        var buffer = ByteBuffer.wrap(encoded);
        var decoded = InterleavedFrameCodec.decode(buffer);

        assertThat(decoded).isPresent();
        assertThat(decoded.get().channel()).isEqualTo(4);
        assertThat(decoded.get().data()).isEqualTo(data);
        assertThat(buffer.remaining()).isEqualTo(0);
    }

    @Test
    void testDecodeEmptyPayload() {
        var frame = new InterleavedFrame(0, new byte[0]);
        byte[] encoded = InterleavedFrameCodec.encode(frame);
        var decoded = InterleavedFrameCodec.decode(encoded, 0);
        assertThat(decoded.data()).isEmpty();
    }

    @Test
    void testEncodeDecodeLargePayload() {
        byte[] data = new byte[1400]; // Max MTU-ish
        for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 256);
        var frame = new InterleavedFrame(0, data);

        byte[] encoded = InterleavedFrameCodec.encode(frame);
        var decoded = InterleavedFrameCodec.decode(encoded, 0);

        assertThat(decoded.data()).isEqualTo(data);
        assertThat(decoded.data().length).isEqualTo(1400);
    }
}
