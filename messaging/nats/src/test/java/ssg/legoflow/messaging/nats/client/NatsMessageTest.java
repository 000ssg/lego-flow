package ssg.legoflow.messaging.nats.client;

import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link NatsMessage}.
 */
class NatsMessageTest {

    @Test
    void testOfWithBytes() {
        var msg = NatsMessage.of("test.subject", "hello".getBytes());
        assertThat(msg.subject()).isEqualTo("test.subject");
        assertThat(msg.replyTo()).isNull();
        assertThat(msg.headers()).isNull();
        assertThat(msg.dataAsString()).isEqualTo("hello");
    }

    @Test
    void testOfWithString() {
        var msg = NatsMessage.of("test", "world");
        assertThat(msg.dataAsString()).isEqualTo("world");
    }

    @Test
    void testOfWithNullString() {
        var msg = NatsMessage.of("test", (String) null);
        assertThat(msg.payload()).isEmpty();
    }

    @Test
    void testWithReplyTo() {
        var msg = NatsMessage.withReplyTo("sub", "_INBOX.1", "data".getBytes());
        assertThat(msg.subject()).isEqualTo("sub");
        assertThat(msg.replyTo()).isEqualTo("_INBOX.1");
        assertThat(msg.hasReplyTo()).isTrue();
    }

    @Test
    void testWithHeaders() {
        var headers = new NatsHeaders();
        headers.set("Key", "Val");
        var msg = NatsMessage.withHeaders("sub", headers, "data".getBytes());
        assertThat(msg.hasHeaders()).isTrue();
        assertThat(msg.headers().getFirst("Key")).isEqualTo("Val");
    }

    @Test
    void testHasReplyTo() {
        assertThat(NatsMessage.of("s", "d").hasReplyTo()).isFalse();
        assertThat(NatsMessage.withReplyTo("s", "r", new byte[0]).hasReplyTo()).isTrue();
    }

    @Test
    void testHasHeaders() {
        assertThat(NatsMessage.of("s", "d").hasHeaders()).isFalse();
    }

    @Test
    void testNullSubjectThrows() {
        assertThatThrownBy(() -> NatsMessage.of(null, "data"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullPayloadBecomesEmpty() {
        var msg = new NatsMessage("s", null, null, null);
        assertThat(msg.payload()).isEmpty();
    }

    @Test
    void testToString() {
        var msg = NatsMessage.of("foo.bar", "hello");
        assertThat(msg.toString()).contains("foo.bar");
        assertThat(msg.toString()).contains("payloadSize=5");
    }
}
