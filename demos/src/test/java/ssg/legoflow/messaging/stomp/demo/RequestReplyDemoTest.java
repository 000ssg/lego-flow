package ssg.legoflow.messaging.stomp.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RequestReplyDemo}.
 *
 * @since 0.1.0
 */
class RequestReplyDemoTest {

    @Test
    void testRequestReply() throws Exception {
        var demo = new RequestReplyDemo();
        try {
            String reply = demo.run("/queue/request", "/queue/reply", "What is 2+2?");
            assertThat(reply).isEqualTo("Reply to: What is 2+2?");
        } finally {
            demo.close();
        }
    }

    @Test
    void testRequestReplyDifferentDestinations() throws Exception {
        var demo = new RequestReplyDemo();
        try {
            String reply = demo.run("/service/calc", "/service/calc/response", "compute");
            assertThat(reply).contains("compute");
        } finally {
            demo.close();
        }
    }
}
