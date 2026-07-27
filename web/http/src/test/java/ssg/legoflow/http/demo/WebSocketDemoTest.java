package ssg.legoflow.http.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpMethod;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.websocket.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class WebSocketDemoTest {

    @Test
    void testHandshakeValidation() {
        // Given: a valid WebSocket upgrade request
        var handshake = new WebSocketHandshake();
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        // When: checking the upgrade
        var isUpgrade = handshake.isWebSocketUpgrade(request);

        // Then: the request is recognized as a WebSocket upgrade
        assertThat(isUpgrade).isTrue();
    }

    @Test
    void testHandshakeResponseCreation() {
        // Given: a valid WebSocket upgrade request
        var handshake = new WebSocketHandshake();
        var request = HttpRequest.of(HttpMethod.GET, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        // When: creating the handshake response
        var response = handshake.createHandshakeResponse(request);

        // Then: the response has 101 Switching Protocols and correct headers
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SWITCHING_PROTOCOLS);
        assertThat(response.getHeaders().get(HttpHeaders.UPGRADE)).isEqualTo("websocket");
        assertThat(response.getHeaders().get(HttpHeaders.CONNECTION)).isEqualTo("Upgrade");
        assertThat(response.getHeaders().get(HttpHeaders.SEC_WEBSOCKET_ACCEPT)).isNotNull();

        // And: the response is valid per the client key
        var isValid = handshake.validateHandshakeResponse(response, "dGhlIHNhbXBsZSBub25jZQ==");
        assertThat(isValid).isTrue();
    }

    @Test
    void testHandshakeRejectsNonGetMethod() {
        // Given: a POST request (not GET) with WebSocket headers
        var handshake = new WebSocketHandshake();
        var request = HttpRequest.of(HttpMethod.POST, "/ws");
        request.getHeaders().set(HttpHeaders.UPGRADE, "websocket");
        request.getHeaders().set(HttpHeaders.CONNECTION, "Upgrade");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_KEY, "dGhlIHNhbXBsZSBub25jZQ==");
        request.getHeaders().set(HttpHeaders.SEC_WEBSOCKET_VERSION, "13");

        // When: checking the upgrade
        var isUpgrade = handshake.isWebSocketUpgrade(request);

        // Then: the request is rejected
        assertThat(isUpgrade).isFalse();
    }

    @Test
    void testFrameEncodeDecodeText() {
        // Given: a text frame
        var codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);
        var original = WebSocketFrame.text("Hello, WebSocket!");

        // When: encoding and then decoding
        ByteBuffer encoded = codec.encodeFrame(original);
        var decoded = codec.decodeFrame(encoded);

        // Then: the decoded frame matches the original
        assertThat(decoded.isFin()).isTrue();
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.TEXT);
        assertThat(decoded.getPayloadText()).isEqualTo("Hello, WebSocket!");
        assertThat(decoded.isMasked()).isFalse();
    }

    @Test
    void testFrameEncodeDecodeBinary() {
        // Given: a binary frame
        var codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);
        var data = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05};
        var original = WebSocketFrame.binary(data);

        // When: encoding and then decoding
        ByteBuffer encoded = codec.encodeFrame(original);
        var decoded = codec.decodeFrame(encoded);

        // Then: the decoded frame matches the original
        assertThat(decoded.isFin()).isTrue();
        assertThat(decoded.getOpCode()).isEqualTo(WebSocketOpCode.BINARY);
        var payloadBuf = decoded.getPayload();
        var payloadBytes = new byte[payloadBuf.remaining()];
        payloadBuf.get(payloadBytes);
        assertThat(payloadBytes).isEqualTo(data);
    }

    @Test
    void testFrameEncodeDecodeControlFrames() {
        // Given: control frames (close, ping, pong)
        var codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.ENCODE);
        var closeFrame = WebSocketFrame.close();
        var pingFrame = WebSocketFrame.ping("ping-data".getBytes());
        var pongFrame = WebSocketFrame.pong("pong-data".getBytes());

        // When: encoding and decoding each
        var decodedClose = codec.decodeFrame(codec.encodeFrame(closeFrame));
        var decodedPing = codec.decodeFrame(codec.encodeFrame(pingFrame));
        var decodedPong = codec.decodeFrame(codec.encodeFrame(pongFrame));

        // Then: opcodes are preserved
        assertThat(decodedClose.getOpCode()).isEqualTo(WebSocketOpCode.CLOSE);
        assertThat(decodedPing.getOpCode()).isEqualTo(WebSocketOpCode.PING);
        assertThat(decodedPong.getOpCode()).isEqualTo(WebSocketOpCode.PONG);
        assertThat(decodedPing.getPayloadText()).isEqualTo("ping-data");
    }

    @Test
    void testSessionMessageHandling() {
        // Given: a WebSocket session with a message handler
        var session = new WebSocketSession("session-1");
        var receivedMessage = new AtomicReference<String>();
        session.onMessage(frame -> receivedMessage.set(frame.getPayloadText()));

        // When: handling a text frame
        assertThat(session.isOpen()).isTrue();
        session.handleFrame(WebSocketFrame.text("Hello from client"));

        // Then: the message handler was invoked
        assertThat(receivedMessage.get()).isEqualTo("Hello from client");
        assertThat(session.getId()).isEqualTo("session-1");
    }

    @Test
    void testSessionCloseLifecycle() {
        // Given: a WebSocket session with a close handler
        var session = new WebSocketSession("session-2");
        var closeCalled = new AtomicBoolean(false);
        session.onClose(frame -> closeCalled.set(true));

        // When: closing the session
        assertThat(session.isOpen()).isTrue();
        session.close();

        // Then: the session is closed and the handler was invoked
        assertThat(session.isOpen()).isFalse();
        assertThat(closeCalled.get()).isTrue();

        // And: closing again has no effect (idempotent)
        closeCalled.set(false);
        session.close();
        assertThat(closeCalled.get()).isFalse();
    }

    @Test
    void testSessionIgnoresFramesAfterClose() {
        // Given: a closed WebSocket session
        var session = new WebSocketSession("session-3");
        var receivedMessage = new AtomicReference<String>();
        session.onMessage(frame -> receivedMessage.set(frame.getPayloadText()));
        session.close();

        // When: handling a frame on a closed session
        session.handleFrame(WebSocketFrame.text("Should be ignored"));

        // Then: the message handler was not invoked
        assertThat(receivedMessage.get()).isNull();
    }
}
