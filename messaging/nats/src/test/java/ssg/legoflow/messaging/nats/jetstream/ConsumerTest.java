package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Consumer}.
 */
class ConsumerTest {

    @Test
    void testBasicProperties() {
        var config = ConsumerConfig.builder().durable("c1").build();
        var consumer = new Consumer("c1", config, 1);

        assertThat(consumer.name()).isEqualTo("c1");
        assertThat(consumer.config()).isEqualTo(config);
        assertThat(consumer.deliveredSequence()).isEqualTo(0);
        assertThat(consumer.ackedSequence()).isEqualTo(0);
    }

    @Test
    void testMarkDelivered() {
        var config = ConsumerConfig.builder().ackPolicy(AckPolicy.EXPLICIT).build();
        var consumer = new Consumer("c", config, 1);

        consumer.markDelivered(1);
        consumer.markDelivered(2);

        assertThat(consumer.deliveredSequence()).isEqualTo(2);
        assertThat(consumer.deliveredCount()).isEqualTo(2);
        assertThat(consumer.pendingCount()).isEqualTo(2);
    }

    @Test
    void testAcknowledgeExplicit() {
        var config = ConsumerConfig.builder().ackPolicy(AckPolicy.EXPLICIT).build();
        var consumer = new Consumer("c", config, 1);

        consumer.markDelivered(1);
        consumer.markDelivered(2);
        consumer.acknowledge(1);

        assertThat(consumer.pendingCount()).isEqualTo(1);
        assertThat(consumer.ackedSequence()).isEqualTo(1);
    }

    @Test
    void testAcknowledgeAll() {
        var config = ConsumerConfig.builder().ackPolicy(AckPolicy.ALL).build();
        var consumer = new Consumer("c", config, 1);

        consumer.markDelivered(1);
        consumer.markDelivered(2);
        consumer.markDelivered(3);
        consumer.acknowledge(2); // acks 1 and 2

        assertThat(consumer.pendingCount()).isEqualTo(1);
        assertThat(consumer.ackedSequence()).isEqualTo(2);
    }

    @Test
    void testAcknowledgeNone() {
        var config = ConsumerConfig.builder().ackPolicy(AckPolicy.NONE).build();
        var consumer = new Consumer("c", config, 1);

        consumer.markDelivered(1);
        assertThat(consumer.pendingCount()).isEqualTo(0);
    }

    @Test
    void testCanDeliver() {
        var config = ConsumerConfig.builder()
                .ackPolicy(AckPolicy.EXPLICIT)
                .maxAckPending(2)
                .build();
        var consumer = new Consumer("c", config, 1);

        assertThat(consumer.canDeliver()).isTrue();
        consumer.markDelivered(1);
        assertThat(consumer.canDeliver()).isTrue();
        consumer.markDelivered(2);
        assertThat(consumer.canDeliver()).isFalse();

        consumer.acknowledge(1);
        assertThat(consumer.canDeliver()).isTrue();
    }

    @Test
    void testCanDeliverNonePolicy() {
        var config = ConsumerConfig.builder().ackPolicy(AckPolicy.NONE).build();
        var consumer = new Consumer("c", config, 1);

        for (int i = 0; i < 100; i++) {
            consumer.markDelivered(i + 1);
        }
        assertThat(consumer.canDeliver()).isTrue();
    }

    @Test
    void testNextFetchSequence() {
        var config = ConsumerConfig.builder().build();
        var consumer = new Consumer("c", config, 1);

        assertThat(consumer.nextFetchSequence()).isEqualTo(1);
        consumer.markDelivered(1);
        assertThat(consumer.nextFetchSequence()).isEqualTo(2);
    }

    @Test
    void testStartSequence() {
        var config = ConsumerConfig.builder().build();
        var consumer = new Consumer("c", config, 50);

        assertThat(consumer.deliveredSequence()).isEqualTo(49);
        assertThat(consumer.nextFetchSequence()).isEqualTo(50);
    }

    @Test
    void testToInfoJson() {
        var config = ConsumerConfig.builder().durable("test").build();
        var consumer = new Consumer("test", config, 1);
        consumer.markDelivered(1);

        String json = consumer.toInfoJson();
        assertThat(json).contains("\"name\":\"test\"");
        assertThat(json).contains("\"config\":");
    }
}
