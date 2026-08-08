package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link StompCodec} — frame parsing, serialization, and header escaping.
 *
 * @since 0.1.0
 */
class StompCodecTest {

    // --- Encoding tests ---

    @Test
    void testEncodeConnectFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ACCEPT_VERSION, "1.0,1.1,1.2");
        headers.put(StompHeaders.HOST, "localhost");
        var frame = new StompFrame(StompCommand.STOMP, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).startsWith("STOMP\n");
        assertThat(result).contains("accept-version:1.0,1.1,1.2\n");
        assertThat(result).contains("host:localhost\n");
        assertThat(result).endsWith("\0");
    }

    @Test
    void testEncodeConnectedFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.VERSION, "1.2");
        headers.put(StompHeaders.SERVER, "TestServer/1.0");
        var frame = new StompFrame(StompCommand.CONNECTED, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).startsWith("CONNECTED\n");
        assertThat(result).contains("version:1.2\n");
    }

    @Test
    void testEncodeSendFrameWithBody() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, "/topic/test");
        headers.put(StompHeaders.CONTENT_TYPE, "text/plain");
        var frame = StompFrame.withText(StompCommand.SEND, headers, "Hello, World!");

        String result = StompCodec.encodeToString(frame);
        assertThat(result).startsWith("SEND\n");
        assertThat(result).contains("destination:/topic/test\n");
        assertThat(result).contains("\n\nHello, World!\0");
    }

    @Test
    void testEncodeMessageFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, "/queue/test");
        headers.put(StompHeaders.MESSAGE_ID, "msg-001");
        headers.put(StompHeaders.SUBSCRIPTION, "sub-1");
        var frame = StompFrame.withText(StompCommand.MESSAGE, headers, "payload");

        byte[] encoded = StompCodec.encode(frame);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("MESSAGE\n");
        assertThat(new String(encoded, StandardCharsets.UTF_8)).contains("message-id:msg-001");
    }

    @Test
    void testEncodeSubscribeFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-0");
        headers.put(StompHeaders.DESTINATION, "/topic/news");
        headers.put(StompHeaders.ACK, "client");
        var frame = new StompFrame(StompCommand.SUBSCRIBE, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("SUBSCRIBE\n");
        assertThat(result).contains("id:sub-0\n");
        assertThat(result).contains("ack:client\n");
    }

    @Test
    void testEncodeUnsubscribeFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "sub-0");
        var frame = new StompFrame(StompCommand.UNSUBSCRIBE, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("UNSUBSCRIBE\n");
    }

    @Test
    void testEncodeAckFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "ack-123");
        var frame = new StompFrame(StompCommand.ACK, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("ACK\n");
        assertThat(result).contains("id:ack-123\n");
    }

    @Test
    void testEncodeNackFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.ID, "nack-456");
        var frame = new StompFrame(StompCommand.NACK, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("NACK\n");
    }

    @Test
    void testEncodeBeginFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, "tx-1");
        var frame = new StompFrame(StompCommand.BEGIN, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("BEGIN\n");
        assertThat(result).contains("transaction:tx-1\n");
    }

    @Test
    void testEncodeCommitFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, "tx-1");
        var frame = new StompFrame(StompCommand.COMMIT, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("COMMIT\n");
    }

    @Test
    void testEncodeAbortFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.TRANSACTION, "tx-1");
        var frame = new StompFrame(StompCommand.ABORT, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("ABORT\n");
    }

    @Test
    void testEncodeDisconnectFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.RECEIPT, "receipt-disconnect");
        var frame = new StompFrame(StompCommand.DISCONNECT, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("DISCONNECT\n");
        assertThat(result).contains("receipt:receipt-disconnect\n");
    }

    @Test
    void testEncodeReceiptFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.RECEIPT_ID, "receipt-123");
        var frame = new StompFrame(StompCommand.RECEIPT, headers);

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("RECEIPT\n");
        assertThat(result).contains("receipt-id:receipt-123\n");
    }

    @Test
    void testEncodeErrorFrame() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.MESSAGE_HEADER, "malformed frame");
        headers.put(StompHeaders.CONTENT_TYPE, "text/plain");
        var frame = StompFrame.withText(StompCommand.ERROR, headers, "Details of the error");

        String result = StompCodec.encodeToString(frame);
        assertThat(result).contains("ERROR\n");
        assertThat(result).contains("message:malformed frame\n");
        assertThat(result).contains("Details of the error");
    }

    @Test
    void testEncodeHeartbeat() {
        var frame = StompFrame.heartbeat();
        byte[] encoded = StompCodec.encode(frame);
        assertThat(encoded).isEqualTo(new byte[]{'\n'});
    }

    @Test
    void testEncodeFrameNoBody() {
        var frame = new StompFrame(StompCommand.DISCONNECT);
        String result = StompCodec.encodeToString(frame);
        assertThat(result).isEqualTo("DISCONNECT\n\n\0");
    }

    // --- Decoding tests ---

    @Test
    void testDecodeConnectFrame() {
        String raw = "CONNECT\naccept-version:1.2\nhost:localhost\n\n\0";
        var frame = StompCodec.decodeFromString(raw);

        assertThat(frame.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(frame.header(StompHeaders.ACCEPT_VERSION)).isEqualTo("1.2");
        assertThat(frame.header(StompHeaders.HOST)).isEqualTo("localhost");
        assertThat(frame.hasBody()).isFalse();
    }

    @Test
    void testDecodeStompCommand() {
        String raw = "STOMP\naccept-version:1.2\nhost:localhost\n\n\0";
        var frame = StompCodec.decodeFromString(raw);
        assertThat(frame.command()).isEqualTo(StompCommand.STOMP);
    }

    @Test
    void testDecodeSendWithBody() {
        String raw = "SEND\ndestination:/topic/test\ncontent-type:text/plain\n\nHello!\0";
        var frame = StompCodec.decodeFromString(raw);

        assertThat(frame.command()).isEqualTo(StompCommand.SEND);
        assertThat(frame.header(StompHeaders.DESTINATION)).isEqualTo("/topic/test");
        assertThat(frame.bodyAsText()).isEqualTo("Hello!");
    }

    @Test
    void testDecodeWithContentLength() {
        String body = "Hello\0World";
        String raw = "SEND\ndestination:/test\ncontent-length:" + body.length() + "\n\n" + body + "\0";
        var frame = StompCodec.decode(raw.getBytes(StandardCharsets.UTF_8));

        assertThat(frame.command()).isEqualTo(StompCommand.SEND);
        assertThat(frame.body()).hasSize(body.length());
        assertThat(frame.bodyAsText()).isEqualTo("Hello\0World");
    }

    @Test
    void testDecodeBinaryBodyWithContentLength() {
        byte[] body = new byte[]{0x00, 0x01, 0x02, (byte) 0xFF, 0x00, 0x03};
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, "/queue/bin");
        headers.put(StompHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        var original = new StompFrame(StompCommand.SEND, headers, body);

        byte[] encoded = StompCodec.encode(original);
        var decoded = StompCodec.decode(encoded);

        assertThat(decoded.body()).isEqualTo(body);
    }

    @Test
    void testDecodeHeartbeat() {
        var frame = StompCodec.decode(new byte[]{'\n'});
        assertThat(frame.isHeartbeat()).isTrue();
    }

    @Test
    void testDecodeMultipleNewlinesAsHeartbeat() {
        var frame = StompCodec.decode(new byte[]{'\n', '\n', '\n'});
        assertThat(frame.isHeartbeat()).isTrue();
    }

    @Test
    void testDecodeWithLeadingNewlines() {
        String raw = "\n\nSEND\ndestination:/test\n\nbody\0";
        var frame = StompCodec.decodeFromString(raw);
        assertThat(frame.command()).isEqualTo(StompCommand.SEND);
        assertThat(frame.bodyAsText()).isEqualTo("body");
    }

    @Test
    void testDecodeWithCRLF() {
        String raw = "CONNECT\r\naccept-version:1.2\r\nhost:localhost\r\n\r\n\0";
        var frame = StompCodec.decodeFromString(raw);
        assertThat(frame.command()).isEqualTo(StompCommand.CONNECT);
        assertThat(frame.header(StompHeaders.ACCEPT_VERSION)).isEqualTo("1.2");
    }

    @Test
    void testDecodeFirstHeaderWins() {
        String raw = "SEND\ndestination:/first\ndestination:/second\n\n\0";
        var frame = StompCodec.decodeFromString(raw);
        assertThat(frame.header(StompHeaders.DESTINATION)).isEqualTo("/first");
    }

    // --- Header escaping tests ---

    @Test
    void testEscapeNewline() {
        String escaped = StompCodec.escapeHeaderValue("line1\nline2");
        assertThat(escaped).isEqualTo("line1\\nline2");
    }

    @Test
    void testEscapeBackslash() {
        String escaped = StompCodec.escapeHeaderValue("path\\to\\file");
        assertThat(escaped).isEqualTo("path\\\\to\\\\file");
    }

    @Test
    void testEscapeColon() {
        String escaped = StompCodec.escapeHeaderValue("key:value");
        assertThat(escaped).isEqualTo("key\\cvalue");
    }

    @Test
    void testEscapeCarriageReturn() {
        String escaped = StompCodec.escapeHeaderValue("line1\rline2");
        assertThat(escaped).isEqualTo("line1\\rline2");
    }

    @Test
    void testUnescapeNewline() {
        String unescaped = StompCodec.unescapeHeaderValue("line1\\nline2");
        assertThat(unescaped).isEqualTo("line1\nline2");
    }

    @Test
    void testUnescapeBackslash() {
        String unescaped = StompCodec.unescapeHeaderValue("path\\\\to");
        assertThat(unescaped).isEqualTo("path\\to");
    }

    @Test
    void testUnescapeColon() {
        String unescaped = StompCodec.unescapeHeaderValue("key\\cvalue");
        assertThat(unescaped).isEqualTo("key:value");
    }

    @Test
    void testUnescapeCarriageReturn() {
        String unescaped = StompCodec.unescapeHeaderValue("line1\\rline2");
        assertThat(unescaped).isEqualTo("line1\rline2");
    }

    @Test
    void testEscapeRoundTrip() {
        String original = "key:with\nnewline\\and\\backslash\rand:colon";
        String escaped = StompCodec.escapeHeaderValue(original);
        String unescaped = StompCodec.unescapeHeaderValue(escaped);
        assertThat(unescaped).isEqualTo(original);
    }

    @Test
    void testEscapedHeadersInFrame() {
        var headers = new StompHeaders();
        headers.put("custom\nheader", "value\nwith:special\\chars");
        var frame = new StompFrame(StompCommand.SEND, headers);

        byte[] encoded = StompCodec.encode(frame);
        var decoded = StompCodec.decode(encoded);

        assertThat(decoded.header("custom\nheader")).isEqualTo("value\nwith:special\\chars");
    }

    // --- Error handling tests ---

    @Test
    void testDecodeEmptyData() {
        assertThatThrownBy(() -> StompCodec.decode(new byte[0]))
                .isInstanceOf(StompProtocolException.class)
                .hasMessageContaining("Empty frame data");
    }

    @Test
    void testDecodeNullData() {
        assertThatThrownBy(() -> StompCodec.decode(null))
                .isInstanceOf(StompProtocolException.class);
    }

    @Test
    void testDecodeUnknownCommand() {
        assertThatThrownBy(() -> StompCodec.decodeFromString("UNKNOWN\n\n\0"))
                .isInstanceOf(StompProtocolException.class)
                .hasMessageContaining("Unknown command");
    }

    @Test
    void testDecodeNoNewlineAfterCommand() {
        assertThatThrownBy(() -> StompCodec.decodeFromString("SEND"))
                .isInstanceOf(StompProtocolException.class);
    }

    // --- Round-trip tests ---

    @Test
    void testRoundTripAllCommands() {
        for (var cmd : StompCommand.values()) {
            if (cmd == StompCommand.HEARTBEAT) continue;
            var original = new StompFrame(cmd);
            byte[] encoded = StompCodec.encode(original);
            var decoded = StompCodec.decode(encoded);
            assertThat(decoded.command()).isEqualTo(cmd);
        }
    }

    @Test
    void testRoundTripWithHeaders() {
        var headers = new StompHeaders();
        headers.put("key1", "value1");
        headers.put("key2", "value2");
        headers.put("key3", "value3");
        var original = new StompFrame(StompCommand.SEND, headers);

        var decoded = StompCodec.decode(StompCodec.encode(original));
        assertThat(decoded.header("key1")).isEqualTo("value1");
        assertThat(decoded.header("key2")).isEqualTo("value2");
        assertThat(decoded.header("key3")).isEqualTo("value3");
    }

    @Test
    void testRoundTripWithBody() {
        var headers = new StompHeaders();
        headers.put(StompHeaders.DESTINATION, "/test");
        String body = "The quick brown fox jumps over the lazy dog.";
        var original = StompFrame.withText(StompCommand.SEND, headers, body);

        var decoded = StompCodec.decode(StompCodec.encode(original));
        assertThat(decoded.bodyAsText()).isEqualTo(body);
    }
}
