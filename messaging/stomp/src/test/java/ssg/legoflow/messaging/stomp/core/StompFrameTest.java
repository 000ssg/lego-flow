package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StompFrame} record.
 *
 * @since 0.1.0
 */
class StompFrameTest {

    @Test
    void testCreateWithCommandOnly() {
        var frame = new StompFrame(StompCommand.CONNECT);
        assertThat(frame.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(frame.headers()).isNotNull();
        assertThat(frame.headers().isEmpty()).isTrue();
        assertThat(frame.body()).isEmpty();
    }

    @Test
    void testCreateWithHeaders() {
        var headers = new StompHeaders();
        headers.put("key", "value");
        var frame = new StompFrame(StompCommand.SEND, headers);
        assertThat(frame.header("key")).isEqualTo("value");
        assertThat(frame.hasBody()).isFalse();
    }

    @Test
    void testCreateWithBody() {
        byte[] body = "test body".getBytes(StandardCharsets.UTF_8);
        var frame = new StompFrame(StompCommand.SEND, new StompHeaders(), body);
        assertThat(frame.hasBody()).isTrue();
        assertThat(frame.bodyAsText()).isEqualTo("test body");
    }

    @Test
    void testWithText() {
        var frame = StompFrame.withText(StompCommand.SEND, new StompHeaders(), "hello");
        assertThat(frame.bodyAsText()).isEqualTo("hello");
        assertThat(frame.body()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testHeartbeat() {
        var frame = StompFrame.heartbeat();
        assertThat(frame.isHeartbeat()).isTrue();
        assertThat(frame.command()).isEqualTo(StompCommand.HEARTBEAT);
    }

    @Test
    void testNonHeartbeat() {
        var frame = new StompFrame(StompCommand.CONNECT);
        assertThat(frame.isHeartbeat()).isFalse();
    }

    @Test
    void testNullCommand() {
        assertThatThrownBy(() -> new StompFrame(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNullHeadersDefaulted() {
        var frame = new StompFrame(StompCommand.SEND, null, new byte[0]);
        assertThat(frame.headers()).isNotNull();
    }

    @Test
    void testNullBodyDefaulted() {
        var frame = new StompFrame(StompCommand.SEND, new StompHeaders(), null);
        assertThat(frame.body()).isNotNull();
        assertThat(frame.body()).isEmpty();
    }

    @Test
    void testToString() {
        var headers = new StompHeaders();
        headers.put("key", "value");
        var frame = StompFrame.withText(StompCommand.SEND, headers, "body");
        String str = frame.toString();
        assertThat(str).contains("SEND");
        assertThat(str).contains("headers=");
        assertThat(str).contains("body=");
    }
}
