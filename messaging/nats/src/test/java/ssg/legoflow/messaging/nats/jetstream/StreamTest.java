package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link Stream}.
 */
class StreamTest {

    @Test
    void testCreateStream() {
        var config = StreamConfig.builder("ORDERS").subjects("orders.>").build();
        var stream = new Stream(config);

        assertThat(stream.name()).isEqualTo("ORDERS");
        assertThat(stream.config()).isEqualTo(config);
        assertThat(stream.store()).isNotNull();
        assertThat(stream.consumerCount()).isEqualTo(0);
    }

    @Test
    void testAddConsumer() {
        var stream = new Stream(StreamConfig.builder("S").subjects("s.>").build());
        var consumer = new Consumer("c1", ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        assertThat(stream.consumerCount()).isEqualTo(1);
        assertThat(stream.getConsumer("c1")).isEqualTo(consumer);
    }

    @Test
    void testRemoveConsumer() {
        var stream = new Stream(StreamConfig.builder("S").subjects("s.>").build());
        var consumer = new Consumer("c1", ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        var removed = stream.removeConsumer("c1");
        assertThat(removed).isEqualTo(consumer);
        assertThat(stream.consumerCount()).isEqualTo(0);
    }

    @Test
    void testRemoveNonExistentConsumer() {
        var stream = new Stream(StreamConfig.builder("S").subjects("s.>").build());
        assertThat(stream.removeConsumer("missing")).isNull();
    }

    @Test
    void testConsumerNames() {
        var stream = new Stream(StreamConfig.builder("S").subjects("s.>").build());
        stream.addConsumer(new Consumer("c1", ConsumerConfig.builder().durable("c1").build(), 1));
        stream.addConsumer(new Consumer("c2", ConsumerConfig.builder().durable("c2").build(), 1));

        assertThat(stream.consumerNames()).containsExactlyInAnyOrder("c1", "c2");
    }

    @Test
    void testMaxConsumers() {
        var config = StreamConfig.builder("S").subjects("s.>").maxConsumers(1).build();
        var stream = new Stream(config);

        stream.addConsumer(new Consumer("c1", ConsumerConfig.builder().durable("c1").build(), 1));

        assertThatThrownBy(() ->
                stream.addConsumer(new Consumer("c2", ConsumerConfig.builder().durable("c2").build(), 1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum consumers");
    }

    @Test
    void testMatchesSubject() {
        var stream = new Stream(StreamConfig.builder("S").subjects("orders.>").build());

        assertThat(stream.matchesSubject("orders.new")).isTrue();
        assertThat(stream.matchesSubject("orders.cancel")).isTrue();
        assertThat(stream.matchesSubject("orders.a.b")).isTrue();
        assertThat(stream.matchesSubject("events.login")).isFalse();
    }

    @Test
    void testMatchesMultipleSubjects() {
        var stream = new Stream(StreamConfig.builder("S")
                .subjects("orders.>", "returns.>").build());

        assertThat(stream.matchesSubject("orders.new")).isTrue();
        assertThat(stream.matchesSubject("returns.new")).isTrue();
        assertThat(stream.matchesSubject("events.x")).isFalse();
    }

    @Test
    void testToInfoJson() {
        var stream = new Stream(StreamConfig.builder("EVENTS").subjects("events.>").build());
        stream.store().store("events.a", null, "data".getBytes());

        String json = stream.toInfoJson();
        assertThat(json).contains("\"config\":");
        assertThat(json).contains("\"state\":");
        assertThat(json).contains("\"messages\":1");
    }
}
