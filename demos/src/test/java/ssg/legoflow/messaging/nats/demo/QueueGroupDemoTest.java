package ssg.legoflow.messaging.nats.demo;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link QueueGroupDemo}.
 */
class QueueGroupDemoTest {

    @Test
    void testQueueGroupDemo() throws IOException, InterruptedException {
        var counts = QueueGroupDemo.run(0, 3, 12);

        // All messages should be distributed
        int total = counts.values().stream().mapToInt(a -> a.get()).sum();
        assertThat(total).isEqualTo(12);

        // Each worker should get at least some messages
        for (var entry : counts.entrySet()) {
            assertThat(entry.getValue().get()).isGreaterThan(0);
        }
    }

    @Test
    void testQueueGroupTwoWorkers() throws IOException, InterruptedException {
        var counts = QueueGroupDemo.run(0, 2, 10);
        int total = counts.values().stream().mapToInt(a -> a.get()).sum();
        assertThat(total).isEqualTo(10);
    }
}
