package ssg.legoflow.messaging.nats.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PubSubDemo}.
 */
class PubSubDemoTest {

    @Test
    void testPubSubDemo() throws IOException, InterruptedException {
        int received = PubSubDemo.run(0);
        assertThat(received).isEqualTo(3);
    }
}
