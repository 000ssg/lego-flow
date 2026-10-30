package ssg.legoflow.messaging.mqtt.transport;

import ssg.legoflow.http.websocket.WebSocketCloseCode;
import ssg.legoflow.http.websocket.WebSocketFrame;
import ssg.legoflow.http.websocket.WebSocketFrameCodec;
import ssg.legoflow.http.websocket.WebSocketSession;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MqttWebSocketTransport}.
 *
 * @since 0.2.0
 */
class MqttWebSocketTransportTest {

    @Test
    void testSendBinaryFrame() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var codec = new WebSocketFrameCodec(WebSocketFrameCodec.Mode.DECODE);
        var transport = new MqttWebSocketTransport(session, sent::add);

        var data = ByteBuffer.wrap("MQTT_PAYLOAD".getBytes(StandardCharsets.UTF_8));
        transport.send(data);

        assertThat(sent).hasSize(1);
        // The sent bytes are a WebSocket wire frame — decode it
        var wireFrame = codec.decodeFrame(sent.get(0));
        assertThat(wireFrame).isNotNull();
        assertThat(wireFrame.getPayloadText()).isEqualTo("MQTT_PAYLOAD");
    }

    @Test
    void testReceiveFromSession() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        // Simulate incoming binary frame from WebSocket layer
        var payload = "HELLO_MQTT";
        var frame = WebSocketFrame.binary(payload.getBytes(StandardCharsets.UTF_8));
        session.handleFrame(frame);

        // Read from transport
        var buffer = ByteBuffer.allocate(256);
        int bytesRead = transport.receiveWithTimeout(buffer, 1, TimeUnit.SECONDS);
        assertThat(bytesRead).isEqualTo(payload.length());
        buffer.flip();
        var received = new String(buffer.array(), 0, bytesRead, StandardCharsets.UTF_8);
        assertThat(received).isEqualTo(payload);
    }

    @Test
    void testCloseHandsOffToSession() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        assertThat(transport.isOpen()).isTrue();
        transport.close();
        assertThat(transport.isOpen()).isFalse();
    }

    @Test
    void testReceiveTimeoutReturnsNegative() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        var buffer = ByteBuffer.allocate(256);
        int bytesRead = transport.receiveWithTimeout(buffer, 100, TimeUnit.MILLISECONDS);
        assertThat(bytesRead).isEqualTo(-1);
    }

    @Test
    void testSendAfterCloseIsNoop() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        transport.close();
        var data = ByteBuffer.wrap("after-close".getBytes(StandardCharsets.UTF_8));
        transport.send(data);

        assertThat(sent).isEmpty();
    }

    @Test
    void testSessionCloseTriggersTransportClose() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        // Simulate WebSocket close frame from peer
        var closeFrame = WebSocketFrame.close(WebSocketCloseCode.NORMAL_CLOSURE.code(), "Normal");
        session.handleFrame(closeFrame);

        // Give the session time to process
        Thread.sleep(50);

        assertThat(transport.isOpen()).isFalse();
    }

    @Test
    void testGetChannelReturnsNull() {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        assertThat(transport.getChannel()).isNull();
    }

    @Test
    void testDefaultReceiveUses5SecondTimeout() throws Exception {
        var sent = new ArrayList<ByteBuffer>();
        var session = new WebSocketSession("test");
        var transport = new MqttWebSocketTransport(session, sent::add);

        var buffer = ByteBuffer.allocate(256);
        long start = System.currentTimeMillis();
        int bytesRead = transport.receive(buffer);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(bytesRead).isEqualTo(-1);
        // Default receive() uses 5s timeout, but returns early if closed
        assertThat(elapsed).isGreaterThanOrEqualTo(4900);
    }
}
