package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PullSubscription}.
 */
class PullSubscriptionTest {

    private Stream stream;

    @BeforeEach
    void setUp() {
        var config = StreamConfig.builder("TEST")
                .subjects("test.>")
                .build();
        stream = new Stream(config);
    }

    @Test
    void testFetchMessages() {
        stream.store().store("test.a", null, "msg1".getBytes());
        stream.store().store("test.b", null, "msg2".getBytes());

        var consumer = new Consumer("c1",
                ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        var messages = pull.fetch(10);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).dataAsString()).isEqualTo("msg1");
        assertThat(messages.get(1).dataAsString()).isEqualTo("msg2");
    }

    @Test
    void testFetchWithMetadataHeaders() {
        stream.store().store("test.x", null, "data".getBytes());

        var consumer = new Consumer("c1",
                ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        var messages = pull.fetch(1);

        assertThat(messages).hasSize(1);
        var msg = messages.getFirst();
        assertThat(msg.headers()).isNotNull();
        assertThat(msg.headers().getFirst("Nats-Stream")).isEqualTo("TEST");
        assertThat(msg.headers().getFirst("Nats-Sequence")).isEqualTo("1");
        assertThat(msg.headers().getFirst("Nats-Timestamp")).isNotNull();
    }

    @Test
    void testFetchEmptyStream() {
        var consumer = new Consumer("c1",
                ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        assertThat(pull.fetch(10)).isEmpty();
    }

    @Test
    void testAck() {
        stream.store().store("test.a", null, "data".getBytes());

        var consumer = new Consumer("c1",
                ConsumerConfig.builder().durable("c1").ackPolicy(AckPolicy.EXPLICIT).build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        var messages = pull.fetch(1);
        assertThat(consumer.pendingCount()).isEqualTo(1);

        pull.ack(messages.getFirst());
        assertThat(consumer.pendingCount()).isEqualTo(0);
    }

    @Test
    void testAckBySequence() {
        stream.store().store("test.a", null, "data".getBytes());

        var consumer = new Consumer("c1",
                ConsumerConfig.builder().durable("c1").build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        pull.fetch(1);

        pull.ack(1);
        assertThat(consumer.pendingCount()).isEqualTo(0);
    }

    @Test
    void testFetchWithFilterSubject() {
        stream.store().store("test.orders", null, "order1".getBytes());
        stream.store().store("test.returns", null, "return1".getBytes());
        stream.store().store("test.orders", null, "order2".getBytes());

        var config = ConsumerConfig.builder()
                .durable("orders-only")
                .filterSubject("test.orders")
                .build();
        var consumer = new Consumer("orders-only", config, 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);
        var messages = pull.fetch(10);

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).dataAsString()).isEqualTo("order1");
        assertThat(messages.get(1).dataAsString()).isEqualTo("order2");
    }

    @Test
    void testWorkqueueRetention() {
        var wqConfig = StreamConfig.builder("WQ")
                .subjects("wq.>")
                .retention(StreamConfig.RetentionPolicy.WORKQUEUE)
                .build();
        var wqStream = new Stream(wqConfig);
        wqStream.store().store("wq.task", null, "task1".getBytes());
        wqStream.store().store("wq.task", null, "task2".getBytes());

        var consumer = new Consumer("worker",
                ConsumerConfig.builder().durable("worker").build(), 1);
        wqStream.addConsumer(consumer);

        var pull = new PullSubscription(wqStream, consumer);
        var messages = pull.fetch(1);
        pull.ack(messages.getFirst());

        // Message should be removed from store after ack
        assertThat(wqStream.store().messageCount()).isEqualTo(1);
    }

    @Test
    void testSequentialFetches() {
        for (int i = 0; i < 5; i++) {
            stream.store().store("test.item", null, ("item-" + i).getBytes());
        }

        var consumer = new Consumer("c",
                ConsumerConfig.builder().durable("c").build(), 1);
        stream.addConsumer(consumer);

        var pull = new PullSubscription(stream, consumer);

        var batch1 = pull.fetch(2);
        assertThat(batch1).hasSize(2);
        assertThat(batch1.get(0).dataAsString()).isEqualTo("item-0");

        var batch2 = pull.fetch(2);
        assertThat(batch2).hasSize(2);
        assertThat(batch2.get(0).dataAsString()).isEqualTo("item-2");

        var batch3 = pull.fetch(2);
        assertThat(batch3).hasSize(1);
        assertThat(batch3.get(0).dataAsString()).isEqualTo("item-4");
    }
}
