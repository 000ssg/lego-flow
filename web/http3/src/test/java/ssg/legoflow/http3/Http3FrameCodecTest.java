package ssg.legoflow.http3;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class Http3FrameCodecTest {

    @Test
    void testEncodeDecodeDataFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var payload = ByteBuffer.wrap("Hello, HTTP/3!".getBytes(StandardCharsets.UTF_8));
        var frame = Http3Frame.data(payload);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.DATA);
        var decodedPayload = new byte[decoded.payload().remaining()];
        decoded.payload().get(decodedPayload);
        assertThat(new String(decodedPayload, StandardCharsets.UTF_8)).isEqualTo("Hello, HTTP/3!");
    }

    @Test
    void testEncodeDecodeHeadersFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var headerBlock = ByteBuffer.wrap(new byte[]{0x01, 0x02, 0x03});
        var frame = Http3Frame.headers(headerBlock);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.HEADERS);
        assertThat(decoded.payloadLength()).isEqualTo(3);
    }

    @Test
    void testEncodeDecodeSettingsFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var settings = new Http3Settings();
        var frame = Http3Frame.settings(settings.encode());

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.SETTINGS);
        assertThat(decoded.payloadLength()).isGreaterThan(0);
    }

    @Test
    void testEncodeDecodeGoawayFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var buf = ByteBuffer.allocate(8);
        Http3FrameCodec.encodeVarInt(buf, 42L);
        buf.flip();
        var frame = Http3Frame.goaway(buf);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.GOAWAY);
    }

    @Test
    void testEncodeDecodeCancelPushFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var buf = ByteBuffer.allocate(8);
        Http3FrameCodec.encodeVarInt(buf, 1L);
        buf.flip();
        var frame = Http3Frame.cancelPush(buf);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.CANCEL_PUSH);
    }

    @Test
    void testEncodeDecodePushPromiseFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var payload = ByteBuffer.wrap(new byte[]{0x00, 0x01, 0x02});
        var frame = Http3Frame.pushPromise(payload);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.PUSH_PROMISE);
    }

    @Test
    void testEncodeDecodeMaxPushIdFrame() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var buf = ByteBuffer.allocate(8);
        Http3FrameCodec.encodeVarInt(buf, 100L);
        buf.flip();
        var frame = Http3Frame.maxPushId(buf);

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.MAX_PUSH_ID);
    }

    @Test
    void testVariableLengthIntegerSmall() {
        // Given
        var buf = ByteBuffer.allocate(16);

        // When
        Http3FrameCodec.encodeVarInt(buf, 37);
        buf.flip();
        long decoded = Http3FrameCodec.decodeVarInt(buf);

        // Then
        assertThat(decoded).isEqualTo(37);
    }

    @Test
    void testVariableLengthIntegerMedium() {
        // Given
        var buf = ByteBuffer.allocate(16);

        // When
        Http3FrameCodec.encodeVarInt(buf, 15293);
        buf.flip();
        long decoded = Http3FrameCodec.decodeVarInt(buf);

        // Then
        assertThat(decoded).isEqualTo(15293);
    }

    @Test
    void testVariableLengthIntegerLarge() {
        // Given
        var buf = ByteBuffer.allocate(16);

        // When
        Http3FrameCodec.encodeVarInt(buf, 494878333L);
        buf.flip();
        long decoded = Http3FrameCodec.decodeVarInt(buf);

        // Then
        assertThat(decoded).isEqualTo(494878333L);
    }

    @Test
    void testVariableLengthIntegerVeryLarge() {
        // Given
        var buf = ByteBuffer.allocate(16);

        // When
        Http3FrameCodec.encodeVarInt(buf, 151288809941952652L);
        buf.flip();
        long decoded = Http3FrameCodec.decodeVarInt(buf);

        // Then
        assertThat(decoded).isEqualTo(151288809941952652L);
    }

    @Test
    void testEmptyPayload() {
        // Given
        var codec = new Http3FrameCodec(Http3FrameCodec.Mode.ENCODE);
        var frame = Http3Frame.data(ByteBuffer.allocate(0));

        // When
        var encoded = codec.encodeFrame(frame);
        var decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.type()).isEqualTo(Http3FrameType.DATA);
        assertThat(decoded.payloadLength()).isEqualTo(0);
    }

    @Test
    void testFrameTypeFromCode() {
        // Given/When/Then
        assertThat(Http3FrameType.fromCode(0x00)).isEqualTo(Http3FrameType.DATA);
        assertThat(Http3FrameType.fromCode(0x01)).isEqualTo(Http3FrameType.HEADERS);
        assertThat(Http3FrameType.fromCode(0x03)).isEqualTo(Http3FrameType.CANCEL_PUSH);
        assertThat(Http3FrameType.fromCode(0x04)).isEqualTo(Http3FrameType.SETTINGS);
        assertThat(Http3FrameType.fromCode(0x05)).isEqualTo(Http3FrameType.PUSH_PROMISE);
        assertThat(Http3FrameType.fromCode(0x07)).isEqualTo(Http3FrameType.GOAWAY);
        assertThat(Http3FrameType.fromCode(0x0D)).isEqualTo(Http3FrameType.MAX_PUSH_ID);
    }

    @Test
    void testFrameTypeUnknownCodeThrows() {
        // Given/When/Then
        assertThatThrownBy(() -> Http3FrameType.fromCode(0xFF))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
