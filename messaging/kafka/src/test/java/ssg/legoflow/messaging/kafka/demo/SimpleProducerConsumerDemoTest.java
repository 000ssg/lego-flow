package ssg.legoflow.messaging.kafka.demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

class SimpleProducerConsumerDemoTest {

    @Test
    void testSimpleDemo() throws IOException {
        int consumed = SimpleProducerConsumerDemo.run(10);
        assertThat(consumed).isEqualTo(10);
    }

    @Test
    void testSimpleDemoSingleMessage() throws IOException {
        int consumed = SimpleProducerConsumerDemo.run(1);
        assertThat(consumed).isEqualTo(1);
    }

    @Test
    void testSimpleDemoManyMessages() throws IOException {
        int consumed = SimpleProducerConsumerDemo.run(100);
        assertThat(consumed).isEqualTo(100);
    }
}
