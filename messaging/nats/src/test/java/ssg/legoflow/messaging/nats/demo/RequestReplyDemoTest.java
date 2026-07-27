package ssg.legoflow.messaging.nats.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RequestReplyDemo}.
 */
class RequestReplyDemoTest {

    @Test
    void testRequestReplyDemo() throws IOException, InterruptedException {
        String result = RequestReplyDemo.run(0);
        assertThat(result).isEqualTo("30");
    }
}
