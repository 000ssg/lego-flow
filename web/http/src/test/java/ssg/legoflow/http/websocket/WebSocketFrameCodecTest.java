package ssg.legoflow.http.websocket;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class WebSocketFrameCodecTest {

    private final WebSocketFrameCodec codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);

    @Test
    void testEncodeDecodeTextFrame() {
        // Given
        var frame = WebSocketFrame.text("Hello WebSocket");

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.isFin()).isTrue();
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.TEXT);
        assertThat(decoded.getPayloadText()).isEqualTo("Hello WebSocket");
        assertThat(decoded.isMasked()).isFalse();
    }

    @Test
    void testEncodeDecodeBinaryFrame() {
        // Given
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        var frame = WebSocketFrame.binary(data);

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.isFin()).isTrue();
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.BINARY);
        var payloadBuf = decoded.getPayload();
        var payloadBytes = new byte[payloadBuf.remaining()];
        payloadBuf.get(payloadBytes);
        assertThat(payloadBytes).isEqualTo(data);
    }

    @Test
    void testEncodeDecodeCloseFrame() {
        // Given
        var frame = WebSocketFrame.close();

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.CLOSE);
        assertThat(decoded.isFin()).isTrue();
        assertThat(decoded.getPayloadLength()).isZero();
    }

    @Test
    void testEncodeDecodePingFrame() {
        // Given
        byte[] pingData = "ping".getBytes(StandardCharsets.UTF_8);
        var frame = WebSocketFrame.ping(pingData);

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.PING);
        assertThat(decoded.getPayloadText()).isEqualTo("ping");
    }

    @Test
    void testEncodeDecodePongFrame() {
        // Given
        byte[] pongData = "pong".getBytes(StandardCharsets.UTF_8);
        var frame = WebSocketFrame.pong(pongData);

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.PONG);
        assertThat(decoded.getPayloadText()).isEqualTo("pong");
    }

    @Test
    void testEncodeDecodeMaskedFrame() {
        // Given
        byte[] maskKey = {0x12, 0x34, 0x56, 0x78};
        var frame = new WebSocketFrame(true, WebSocketOpCode.TEXT, true,
                ByteBuffer.wrap("masked".getBytes(StandardCharsets.UTF_8)),
                ByteBuffer.wrap(maskKey));

        // When
        ByteBuffer encoded = codec.encodeFrame(frame);
        WebSocketFrame decoded = codec.decodeFrame(encoded);

        // Then
        assertThat(decoded.isMasked()).isTrue();
        assertThat(decoded.getPayloadText()).isEqualTo("masked");
    }

    @Test
    void testDecodeTooShortThrows() {
        // When/Then
        assertThatThrownBy(() -> codec.decodeFrame(ByteBuffer.wrap(new byte[]{0x01})))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void testControlFrameOpCodes() {
        assertThat(WebSocketOpCode.CLOSE.isControl()).isTrue();
        assertThat(WebSocketOpCode.PING.isControl()).isTrue();
        assertThat(WebSocketOpCode.PONG.isControl()).isTrue();
        assertThat(WebSocketOpCode.TEXT.isControl()).isFalse();
        assertThat(WebSocketOpCode.BINARY.isControl()).isFalse();
    }
}
