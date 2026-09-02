package ssg.legoflow.messaging.nats.demo;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link JetStreamDemo}.
 */
class JetStreamDemoTest {

    @Test
    void testJetStreamDemo() throws IOException {
        var consumed = JetStreamDemo.run(0);
        assertThat(consumed).hasSize(5);
        assertThat(consumed.get(0).dataAsString()).isEqualTo("order-1");
        assertThat(consumed.get(4).dataAsString()).isEqualTo("order-5");
    }
}
