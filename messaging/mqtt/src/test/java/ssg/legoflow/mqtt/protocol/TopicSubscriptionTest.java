package ssg.legoflow.mqtt.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TopicSubscription}.
 *
 * @since 1.0.0
 */
class TopicSubscriptionTest {

    @Test
    void testSimpleConstructor() {
        // Given/When: simple subscription
        var sub = new TopicSubscription("test/topic", QoS.AT_LEAST_ONCE);

        // Then: defaults are applied
        assertThat(sub.topicFilter()).isEqualTo("test/topic");
        assertThat(sub.qos()).isEqualTo(QoS.AT_LEAST_ONCE);
        assertThat(sub.noLocal()).isFalse();
        assertThat(sub.retainAsPublished()).isFalse();
        assertThat(sub.retainHandling()).isEqualTo(RetainHandling.SEND_ON_SUBSCRIBE);
    }

    @Test
    void testFullConstructor() {
        // Given/When: subscription with all options
        var sub = new TopicSubscription("test/+/data", QoS.EXACTLY_ONCE,
                true, true, RetainHandling.DO_NOT_SEND);

        // Then: all values are set
        assertThat(sub.topicFilter()).isEqualTo("test/+/data");
        assertThat(sub.qos()).isEqualTo(QoS.EXACTLY_ONCE);
        assertThat(sub.noLocal()).isTrue();
        assertThat(sub.retainAsPublished()).isTrue();
        assertThat(sub.retainHandling()).isEqualTo(RetainHandling.DO_NOT_SEND);
    }

    @Test
    void testRecordEquality() {
        // Given: two identical subscriptions
        var sub1 = new TopicSubscription("a/b", QoS.AT_MOST_ONCE);
        var sub2 = new TopicSubscription("a/b", QoS.AT_MOST_ONCE);

        // When/Then: they are equal
        assertThat(sub1).isEqualTo(sub2);
    }

    @Test
    void testRecordInequality() {
        // Given: two different subscriptions
        var sub1 = new TopicSubscription("a/b", QoS.AT_MOST_ONCE);
        var sub2 = new TopicSubscription("a/c", QoS.AT_MOST_ONCE);

        // When/Then: they are not equal
        assertThat(sub1).isNotEqualTo(sub2);
    }

    @Test
    void testWildcardTopicFilter() {
        // Given/When: subscription with wildcards
        var sub = new TopicSubscription("sensors/#", QoS.AT_LEAST_ONCE);

        // Then: filter is stored as-is
        assertThat(sub.topicFilter()).isEqualTo("sensors/#");
    }
}
