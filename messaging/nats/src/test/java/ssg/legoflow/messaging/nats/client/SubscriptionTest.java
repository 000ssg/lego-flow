package ssg.legoflow.messaging.nats.client;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link Subscription}.
 */
class SubscriptionTest {

    @Test
    void testBasicProperties() {
        var sub = new Subscription("1", "foo.bar", null, msg -> {});
        assertThat(sub.sid()).isEqualTo("1");
        assertThat(sub.subject()).isEqualTo("foo.bar");
        assertThat(sub.queueGroup()).isNull();
        assertThat(sub.isActive()).isTrue();
    }

    @Test
    void testWithQueueGroup() {
        var sub = new Subscription("2", "tasks", "workers", msg -> {});
        assertThat(sub.queueGroup()).isEqualTo("workers");
    }

    @Test
    void testDeliver() {
        var count = new AtomicInteger(0);
        var sub = new Subscription("1", "test", null, msg -> count.incrementAndGet());

        boolean active = sub.deliver(NatsMessage.of("test", "data"));
        assertThat(active).isTrue();
        assertThat(count.get()).isEqualTo(1);
        assertThat(sub.receivedCount()).isEqualTo(1);
    }

    @Test
    void testAutoUnsubscribe() {
        var count = new AtomicInteger(0);
        var sub = new Subscription("1", "test", null, msg -> count.incrementAndGet());
        sub.setAutoUnsubscribe(2);

        assertThat(sub.maxMessages()).isEqualTo(2);
        assertThat(sub.deliver(NatsMessage.of("test", "1"))).isTrue();
        assertThat(sub.deliver(NatsMessage.of("test", "2"))).isFalse();
        assertThat(sub.isActive()).isFalse();
        assertThat(count.get()).isEqualTo(2);
    }

    @Test
    void testAutoUnsubscribeAfterOne() {
        var sub = new Subscription("1", "test", null, msg -> {});
        sub.setAutoUnsubscribe(1);

        assertThat(sub.deliver(NatsMessage.of("test", "1"))).isFalse();
        assertThat(sub.isActive()).isFalse();
    }

    @Test
    void testUnsubscribe() {
        var sub = new Subscription("1", "test", null, msg -> {});
        sub.unsubscribe();
        assertThat(sub.isActive()).isFalse();
        assertThat(sub.deliver(NatsMessage.of("test", "data"))).isFalse();
    }

    @Test
    void testDeliverAfterUnsubscribe() {
        var count = new AtomicInteger(0);
        var sub = new Subscription("1", "test", null, msg -> count.incrementAndGet());
        sub.unsubscribe();
        sub.deliver(NatsMessage.of("test", "data"));
        assertThat(count.get()).isEqualTo(0);
    }

    @Test
    void testNullSidThrows() {
        assertThatThrownBy(() -> new Subscription(null, "s", null, msg -> {}))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullSubjectThrows() {
        assertThatThrownBy(() -> new Subscription("1", null, null, msg -> {}))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullHandlerThrows() {
        assertThatThrownBy(() -> new Subscription("1", "s", null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
