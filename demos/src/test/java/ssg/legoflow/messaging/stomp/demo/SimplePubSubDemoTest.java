package ssg.legoflow.messaging.stomp.demo;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.stomp.core.StompHeaders;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SimplePubSubDemo}.
 *
 * @since 0.1.0
 */
class SimplePubSubDemoTest {

    @Test
    void testSimplePubSub() throws Exception {
        var demo = new SimplePubSubDemo();
        try {
            var messages = List.of("Hello", "World", "STOMP");
            var received = demo.run("/topic/demo", messages);

            assertThat(received).hasSize(3);
            assertThat(received.get(0).bodyAsText()).isEqualTo("Hello");
            assertThat(received.get(1).bodyAsText()).isEqualTo("World");
            assertThat(received.get(2).bodyAsText()).isEqualTo("STOMP");
        } finally {
            demo.close();
        }
    }

    @Test
    void testPubSubMessageHeaders() throws Exception {
        var demo = new SimplePubSubDemo();
        try {
            var received = demo.run("/queue/work", List.of("task1"));

            assertThat(received).hasSize(1);
            var msg = received.getFirst();
            assertThat(msg.header(StompHeaders.DESTINATION)).isEqualTo("/queue/work");
            assertThat(msg.header(StompHeaders.MESSAGE_ID)).isNotNull();
            assertThat(msg.header(StompHeaders.SUBSCRIPTION)).isNotNull();
        } finally {
            demo.close();
        }
    }

    @Test
    void testPubSubEmptyList() throws Exception {
        var demo = new SimplePubSubDemo();
        try {
            var received = demo.run("/topic/empty", List.of());
            assertThat(received).isEmpty();
        } finally {
            demo.close();
        }
    }

    @Test
    void testPubSubLargePayload() throws Exception {
        var demo = new SimplePubSubDemo();
        try {
            String largeMsg = "x".repeat(10000);
            var received = demo.run("/topic/large", List.of(largeMsg));

            assertThat(received).hasSize(1);
            assertThat(received.getFirst().bodyAsText()).isEqualTo(largeMsg);
        } finally {
            demo.close();
        }
    }
}
