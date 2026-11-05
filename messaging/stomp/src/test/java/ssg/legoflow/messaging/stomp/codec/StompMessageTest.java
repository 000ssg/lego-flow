package ssg.legoflow.messaging.stomp.codec;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.StompMessage;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for StompMessage — the legacy message class (0% coverage). */
class StompMessageTest {

    @Test
    void constructorWithNullHeaders() {
        StompMessage msg = new StompMessage("CONNECT", null, null);
        assertEquals("CONNECT", msg.getCommand());
        assertTrue(msg.getHeaders().isEmpty());
        assertFalse(msg.hasBody());
    }

    @Test
    void constructorWithNullBody() {
        StompMessage msg = new StompMessage("SEND", Collections.emptyMap(), null);
        assertEquals(0, msg.getBodyLength());
        assertFalse(msg.hasBody());
    }

    @Test
    void constructorWithBody() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        StompMessage msg = new StompMessage("SEND", Collections.emptyMap(), body);
        assertTrue(msg.hasBody());
        assertEquals(5, msg.getBodyLength());
        assertArrayEquals(body, msg.getBody());
    }

    @Test
    void bodyIsDefensiveCopy() {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        StompMessage msg = new StompMessage("SEND", Collections.emptyMap(), body);
        body[0] = 'Z';
        assertEquals('h', msg.getBody()[0]);
    }

    @Test
    void headersAreDefensiveCopy() {
        Map<String, String> headers = new HashMap<>();
        headers.put("key", "value");
        StompMessage msg = new StompMessage("SEND", headers, null);
        headers.put("extra", "data");
        assertEquals(1, msg.getHeaders().size());
    }

    @Test
    void getBodyReturnsCopy() {
        StompMessage msg = new StompMessage("SEND", Collections.emptyMap(), "hello".getBytes(StandardCharsets.UTF_8));
        byte[] body1 = msg.getBody();
        byte[] body2 = msg.getBody();
        assertNotSame(body1, body2);
        assertArrayEquals(body1, body2);
    }

    @Test
    void toStringContainsCommand() {
        StompMessage msg = new StompMessage("CONNECT", Collections.emptyMap(), null);
        String s = msg.toString();
        assertTrue(s.contains("CONNECT"));
        assertTrue(s.contains("StompMessage"));
    }

    @Test
    void emptyCommand() {
        StompMessage msg = new StompMessage("", Collections.emptyMap(), null);
        assertEquals("", msg.getCommand());
    }
}
