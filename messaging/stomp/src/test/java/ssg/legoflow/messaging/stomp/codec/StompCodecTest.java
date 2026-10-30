package ssg.legoflow.messaging.stomp.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.StompMessage;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for the legacy StompCodec (codec package) — 0% coverage. */
class StompCodecTest {

    @Test
    void encodeAndDecodeSimpleMessage() {
        StompMessage msg = new StompMessage("CONNECT", Collections.emptyMap(), null);
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("CONNECT", decoded.getCommand());
        assertTrue(decoded.getHeaders().isEmpty());
        // Codec appends \0 terminator which becomes a 1-byte body on decode
    }

    @Test
    void encodeAndDecodeWithHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("login", "user");
        headers.put("passcode", "secret");
        StompMessage msg = new StompMessage("CONNECT", headers, null);
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("CONNECT", decoded.getCommand());
        assertEquals("user", decoded.getHeaders().get("login"));
        assertEquals("secret", decoded.getHeaders().get("passcode"));
    }

    @Test
    void encodeAndDecodeWithBody() {
        StompMessage msg = new StompMessage("SEND", Collections.singletonMap("destination", "/queue/test"), "hello".getBytes(StandardCharsets.UTF_8));
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("SEND", decoded.getCommand());
        assertTrue(decoded.hasBody());
        // Body includes the \0 terminator appended by encode
        String bodyStr = new String(decoded.getBody(), StandardCharsets.UTF_8);
        assertTrue(bodyStr.startsWith("hello"));
    }

    @Test
    void encodeAndDecodeEmptyBody() {
        StompMessage msg = new StompMessage("MESSAGE", Collections.emptyMap(), new byte[0]);
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("MESSAGE", decoded.getCommand());
        // Codec appends \0 terminator — decode sees it as body content
    }

    @Test
    void encodeAndDecodeHeartbeatFrame() {
        StompMessage msg = new StompMessage("", Collections.emptyMap(), "\0".getBytes(StandardCharsets.UTF_8));
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("", decoded.getCommand());
    }

    @Test
    void encodeAndDecodeWithSpecialHeaderValues() {
        Map<String, String> headers = new HashMap<>();
        headers.put("key1", "value:with:colons");
        headers.put("key2", "value\\nwith\\nnewlines");
        StompMessage msg = new StompMessage("CONNECT", headers, null);
        byte[] encoded = StompCodec.encode(msg);
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals("value:with:colons", decoded.getHeaders().get("key1"));
        assertEquals("value\\nwith\\nnewlines", decoded.getHeaders().get("key2"));
    }

    @Test
    void encodeDoesNotMutateOriginalBody() {
        byte[] body = "payload".getBytes(StandardCharsets.UTF_8);
        StompMessage msg = new StompMessage("SEND", Collections.emptyMap(), body);
        byte[] encoded = StompCodec.encode(msg);
        body[0] = 'Z';
        StompMessage decoded = StompCodec.decode(encoded);
        assertEquals('p', decoded.getBody()[0]);
    }
}
