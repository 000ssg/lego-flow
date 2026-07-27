package ssg.legoflow.messaging.kafka.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class AdminClientDemoTest {

    @Test
    void testAdminDemo() throws IOException {
        var metadata = AdminClientDemo.run();
        assertThat(metadata).isNotNull();
        assertThat(metadata.brokers()).isNotEmpty();
        // After deleting topic-b, only topic-a should remain
        assertThat(metadata.topics()).hasSize(1);
        assertThat(metadata.topics().getFirst().name()).isEqualTo("topic-a");
    }
}
