package ssg.legoflow.messaging.stomp.core;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link StompHeaders}.
 *
 * @since 0.1.0
 */
class StompHeadersTest {

    @Test
    void testPutAndGet() {
        var headers = new StompHeaders();
        headers.put("key", "value");
        assertThat(headers.get("key")).isEqualTo("value");
    }

    @Test
    void testPutIfAbsent() {
        var headers = new StompHeaders();
        headers.put("key", "first");
        headers.putIfAbsent("key", "second");
        assertThat(headers.get("key")).isEqualTo("first");
    }

    @Test
    void testGetOrDefault() {
        var headers = new StompHeaders();
        assertThat(headers.getOrDefault("missing", "default")).isEqualTo("default");
    }

    @Test
    void testContains() {
        var headers = new StompHeaders();
        headers.put("key", "value");
        assertThat(headers.contains("key")).isTrue();
        assertThat(headers.contains("other")).isFalse();
    }

    @Test
    void testRemove() {
        var headers = new StompHeaders();
        headers.put("key", "value");
        headers.remove("key");
        assertThat(headers.contains("key")).isFalse();
    }

    @Test
    void testSize() {
        var headers = new StompHeaders();
        assertThat(headers.size()).isZero();
        headers.put("a", "1");
        headers.put("b", "2");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void testIsEmpty() {
        var headers = new StompHeaders();
        assertThat(headers.isEmpty()).isTrue();
        headers.put("a", "1");
        assertThat(headers.isEmpty()).isFalse();
    }

    @Test
    void testCaseSensitive() {
        var headers = new StompHeaders();
        headers.put("Content-Type", "text/plain");
        assertThat(headers.get("Content-Type")).isEqualTo("text/plain");
        assertThat(headers.get("content-type")).isNull();
    }

    @Test
    void testToMap() {
        var headers = new StompHeaders();
        headers.put("a", "1");
        headers.put("b", "2");
        var map = headers.toMap();
        assertThat(map).containsEntry("a", "1");
        assertThat(map).containsEntry("b", "2");
        assertThatThrownBy(() -> map.put("c", "3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testCopyConstructor() {
        var original = new StompHeaders();
        original.put("key", "value");
        var copy = new StompHeaders(original);
        copy.put("key", "modified");
        assertThat(original.get("key")).isEqualTo("value");
        assertThat(copy.get("key")).isEqualTo("modified");
    }

    @Test
    void testMapConstructor() {
        var map = Map.of("a", "1", "b", "2");
        var headers = new StompHeaders(map);
        assertThat(headers.get("a")).isEqualTo("1");
        assertThat(headers.get("b")).isEqualTo("2");
    }

    @Test
    void testIterator() {
        var headers = new StompHeaders();
        headers.put("a", "1");
        headers.put("b", "2");
        int count = 0;
        for (var entry : headers) {
            assertThat(entry.getValue()).isNotNull();
            count++;
        }
        assertThat(count).isEqualTo(2);
    }

    @Test
    void testEquals() {
        var h1 = new StompHeaders();
        h1.put("a", "1");
        var h2 = new StompHeaders();
        h2.put("a", "1");
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    void testStandardHeaderConstants() {
        assertThat(StompHeaders.HOST).isEqualTo("host");
        assertThat(StompHeaders.ACCEPT_VERSION).isEqualTo("accept-version");
        assertThat(StompHeaders.DESTINATION).isEqualTo("destination");
        assertThat(StompHeaders.MESSAGE_ID).isEqualTo("message-id");
        assertThat(StompHeaders.CONTENT_TYPE).isEqualTo("content-type");
        assertThat(StompHeaders.CONTENT_LENGTH).isEqualTo("content-length");
        assertThat(StompHeaders.HEART_BEAT).isEqualTo("heart-beat");
        assertThat(StompHeaders.RECEIPT).isEqualTo("receipt");
        assertThat(StompHeaders.RECEIPT_ID).isEqualTo("receipt-id");
        assertThat(StompHeaders.TRANSACTION).isEqualTo("transaction");
    }
}
