package ssg.legoflow.messaging.nats.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link NatsHeaders}.
 */
class NatsHeadersTest {

    @Test
    void testAddAndGetFirst() {
        var headers = new NatsHeaders();
        headers.add("X-Key", "value1");
        assertThat(headers.getFirst("X-Key")).isEqualTo("value1");
    }

    @Test
    void testAddMultipleValues() {
        var headers = new NatsHeaders();
        headers.add("X-Key", "v1");
        headers.add("X-Key", "v2");
        assertThat(headers.getAll("X-Key")).containsExactly("v1", "v2");
        assertThat(headers.getFirst("X-Key")).isEqualTo("v1");
    }

    @Test
    void testSetReplacesValues() {
        var headers = new NatsHeaders();
        headers.add("X-Key", "old");
        headers.set("X-Key", "new");
        assertThat(headers.getFirst("X-Key")).isEqualTo("new");
        assertThat(headers.getAll("X-Key")).hasSize(1);
    }

    @Test
    void testCaseInsensitiveLookup() {
        var headers = new NatsHeaders();
        headers.set("Content-Type", "text/plain");
        assertThat(headers.getFirst("content-type")).isEqualTo("text/plain");
        assertThat(headers.contains("CONTENT-TYPE")).isTrue();
    }

    @Test
    void testContains() {
        var headers = new NatsHeaders();
        headers.set("Key", "val");
        assertThat(headers.contains("Key")).isTrue();
        assertThat(headers.contains("Missing")).isFalse();
    }

    @Test
    void testKeysAndSize() {
        var headers = new NatsHeaders();
        headers.set("A", "1");
        headers.set("B", "2");
        assertThat(headers.keys()).containsExactlyInAnyOrder("A", "B");
        assertThat(headers.size()).isEqualTo(2);
    }

    @Test
    void testEmpty() {
        var headers = new NatsHeaders();
        assertThat(headers.isEmpty()).isTrue();
        headers.set("K", "V");
        assertThat(headers.isEmpty()).isFalse();
    }

    @Test
    void testStatusHeaders() {
        var headers = new NatsHeaders(NatsStatus.NO_MESSAGES, "No Messages");
        assertThat(headers.status()).isEqualTo(NatsStatus.NO_MESSAGES);
        assertThat(headers.statusDescription()).isEqualTo("No Messages");
    }

    @Test
    void testSerializeSimple() {
        var headers = new NatsHeaders();
        headers.set("Key", "Value");
        String serialized = headers.serialize();

        assertThat(serialized).startsWith("NATS/1.0\r\n");
        assertThat(serialized).contains("Key: Value\r\n");
        assertThat(serialized).endsWith("\r\n\r\n");
    }

    @Test
    void testSerializeWithStatus() {
        var headers = new NatsHeaders(NatsStatus.IDLE_HEARTBEAT, "Idle Heartbeat");
        String serialized = headers.serialize();
        assertThat(serialized).startsWith("NATS/1.0 100 Idle Heartbeat\r\n");
    }

    @Test
    void testParseSimple() {
        String raw = "NATS/1.0\r\nKey: Value\r\n\r\n";
        var headers = NatsHeaders.parse(raw);

        assertThat(headers.status()).isNull();
        assertThat(headers.getFirst("Key")).isEqualTo("Value");
    }

    @Test
    void testParseWithStatus() {
        String raw = "NATS/1.0 404 No Messages\r\n\r\n";
        var headers = NatsHeaders.parse(raw);

        assertThat(headers.status()).isEqualTo(NatsStatus.NO_MESSAGES);
        assertThat(headers.statusDescription()).isEqualTo("No Messages");
    }

    @Test
    void testParseMultipleHeaders() {
        String raw = "NATS/1.0\r\nX-A: 1\r\nX-B: 2\r\nX-C: 3\r\n\r\n";
        var headers = NatsHeaders.parse(raw);

        assertThat(headers.getFirst("X-A")).isEqualTo("1");
        assertThat(headers.getFirst("X-B")).isEqualTo("2");
        assertThat(headers.getFirst("X-C")).isEqualTo("3");
        assertThat(headers.size()).isEqualTo(3);
    }

    @Test
    void testRoundTrip() {
        var original = new NatsHeaders();
        original.set("Content-Type", "application/json");
        original.add("X-Tag", "tag1");
        original.add("X-Tag", "tag2");

        var parsed = NatsHeaders.parse(original.serialize());
        assertThat(parsed.getFirst("Content-Type")).isEqualTo("application/json");
        assertThat(parsed.getAll("X-Tag")).containsExactly("tag1", "tag2");
    }

    @Test
    void testRoundTripWithStatus() {
        var original = new NatsHeaders(NatsStatus.REQUEST_TIMEOUT, "Request Timeout");
        original.set("Detail", "extra info");

        var parsed = NatsHeaders.parse(original.serialize());
        assertThat(parsed.status()).isEqualTo(NatsStatus.REQUEST_TIMEOUT);
        assertThat(parsed.getFirst("Detail")).isEqualTo("extra info");
    }

    @Test
    void testParseInvalidThrows() {
        assertThatThrownBy(() -> NatsHeaders.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsHeaders.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NatsHeaders.parse("INVALID/1.0\r\n"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testGetAllMissingKey() {
        var headers = new NatsHeaders();
        assertThat(headers.getAll("missing")).isEmpty();
    }

    @Test
    void testGetFirstMissingKey() {
        var headers = new NatsHeaders();
        assertThat(headers.getFirst("missing")).isNull();
    }
}
