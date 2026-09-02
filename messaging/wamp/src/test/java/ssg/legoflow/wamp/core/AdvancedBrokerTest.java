package ssg.legoflow.wamp.core;

import ssg.legoflow.wamp.core.router.Broker;
import ssg.legoflow.wamp.core.transport.InMemoryTransport;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for Broker Advanced Profile features: pattern-based subscriptions,
 * publisher exclusion, subscriber filtering, publisher identification, and event retention.
 */
class AdvancedBrokerTest {

    // --- Pattern-based subscriptions ---

    @Test
    void testPrefixSubscriptionMatchesTopic() {
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        // Subscribe with prefix match to "com.example."
        var subscribe = new WampMessage.Subscribe(1L, Map.of("match", "prefix"), "com.example.");
        broker.handleSubscribe(subscribe, subPair[0], 10);

        // Publish to "com.example.foo" — should match prefix
        var publish = new WampMessage.Publish(2L, Map.of("exclude_me", false), "com.example.foo", List.of("data"));
        broker.handlePublish(publish, pubPair[0], 20);

        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
        assertThat(((WampMessage.Event) event).args()).containsExactly("data");
    }

    @Test
    void testPrefixSubscriptionDoesNotMatchUnrelatedTopic() {
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of("match", "prefix"), "com.foo."), subPair[0], 10);

        // Publish to unrelated topic
        broker.handlePublish(new WampMessage.Publish(2L, Map.of("exclude_me", false), "com.bar.test", List.of("data")),
                pubPair[0], 20);

        assertThat(subPair[0].tryReceive()).isNull();
    }

    @Test
    void testWildcardSubscriptionMatches() {
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        // "com..bar" should match "com.anything.bar"
        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of("match", "wildcard"), "com..bar"), subPair[0], 10);

        broker.handlePublish(new WampMessage.Publish(2L, Map.of("exclude_me", false), "com.anything.bar", List.of("wild")),
                pubPair[0], 20);

        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
        assertThat(((WampMessage.Event) event).args()).containsExactly("wild");
    }

    @Test
    void testWildcardSubscriptionDoesNotMatchDifferentLength() {
        assertThat(Broker.matchesWildcard("com.x.y.z", "com..bar")).isFalse();
    }

    @Test
    void testWildcardMatchesExactSegments() {
        assertThat(Broker.matchesWildcard("com.test.bar", "com..bar")).isTrue();
        assertThat(Broker.matchesWildcard("com.test.baz", "com..bar")).isFalse();
    }

    // --- Publisher exclusion ---

    @Test
    void testPublisherExcludedByDefault() {
        var broker = new Broker();
        var pair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), pair[0], 10);

        // Publisher is session 10, same as subscriber — should be excluded by default
        broker.handlePublish(new WampMessage.Publish(2L, Map.of(), "topic", List.of("data")),
                pair[0], 10);

        // Subscriber should NOT receive (publisher excluded by default)
        assertThat(pair[0].tryReceive()).isNull();
    }

    @Test
    void testPublisherNotExcludedWhenExcludeMeFalse() {
        var broker = new Broker();
        var pair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), pair[0], 10);

        broker.handlePublish(new WampMessage.Publish(2L, Map.of("exclude_me", false), "topic", List.of("data")),
                pair[0], 10);

        var event = pair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
    }

    // --- Subscriber black/white listing ---

    @Test
    void testSubscriberBlacklist() {
        var broker = new Broker();
        var sub1Pair = InMemoryTransport.createPair();
        var sub2Pair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), sub1Pair[0], 10);
        broker.handleSubscribe(new WampMessage.Subscribe(2L, Map.of(), "topic"), sub2Pair[0], 20);

        // Exclude session 10 from receiving
        broker.handlePublish(new WampMessage.Publish(3L,
                Map.of("exclude", List.of(10), "exclude_me", false),
                "topic", List.of("data")), pubPair[0], 30);

        assertThat(sub1Pair[0].tryReceive()).isNull(); // excluded
        var event = sub2Pair[1].receive(); // not excluded
        assertThat(event).isInstanceOf(WampMessage.Event.class);
    }

    @Test
    void testSubscriberWhitelist() {
        var broker = new Broker();
        var sub1Pair = InMemoryTransport.createPair();
        var sub2Pair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), sub1Pair[0], 10);
        broker.handleSubscribe(new WampMessage.Subscribe(2L, Map.of(), "topic"), sub2Pair[0], 20);

        // Only session 20 is eligible
        broker.handlePublish(new WampMessage.Publish(3L,
                Map.of("eligible", List.of(20), "exclude_me", false),
                "topic", List.of("data")), pubPair[0], 30);

        assertThat(sub1Pair[0].tryReceive()).isNull(); // not eligible
        var event = sub2Pair[1].receive(); // eligible
        assertThat(event).isInstanceOf(WampMessage.Event.class);
    }

    // --- Publisher identification ---

    @Test
    void testPublisherIdentificationDisclosed() {
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), subPair[0], 10);

        broker.handlePublish(new WampMessage.Publish(2L,
                Map.of("disclose_me", true, "exclude_me", false),
                "topic", List.of("data")), pubPair[0], 99);

        var event = (WampMessage.Event) subPair[1].receive();
        assertThat(event.details()).containsEntry("publisher", 99L);
    }

    @Test
    void testPublisherIdentificationNotDisclosedByDefault() {
        var broker = new Broker();
        var subPair = InMemoryTransport.createPair();
        var pubPair = InMemoryTransport.createPair();

        broker.handleSubscribe(new WampMessage.Subscribe(1L, Map.of(), "topic"), subPair[0], 10);

        broker.handlePublish(new WampMessage.Publish(2L, Map.of("exclude_me", false),
                "topic", List.of("data")), pubPair[0], 99);

        var event = (WampMessage.Event) subPair[1].receive();
        assertThat(event.details()).doesNotContainKey("publisher");
    }

    // --- Event retention ---

    @Test
    void testEventRetention() {
        var broker = new Broker();
        var pubPair = InMemoryTransport.createPair();

        // Publish with retain=true
        broker.handlePublish(new WampMessage.Publish(1L,
                Map.of("retain", true, "exclude_me", false),
                "topic.retained", List.of("old-data")), pubPair[0], 10);

        assertThat(broker.getRetainedEvent("topic.retained")).isNotNull();
        assertThat(broker.getRetainedEvent("topic.retained").args()).containsExactly("old-data");
    }

    @Test
    void testRetainedEventDeliveredToNewSubscriber() {
        var broker = new Broker();
        var pubPair = InMemoryTransport.createPair();

        // Publish retained event first
        broker.handlePublish(new WampMessage.Publish(1L,
                Map.of("retain", true, "exclude_me", false),
                "topic.retained", List.of("retained-data")), pubPair[0], 10);

        // New subscriber should get the retained event
        var subPair = InMemoryTransport.createPair();
        broker.handleSubscribe(new WampMessage.Subscribe(2L, Map.of(), "topic.retained"), subPair[0], 20);

        var event = subPair[1].receive();
        assertThat(event).isInstanceOf(WampMessage.Event.class);
        assertThat(((WampMessage.Event) event).args()).containsExactly("retained-data");
    }

    @Test
    void testClearRetainedEvents() {
        var broker = new Broker();
        var pubPair = InMemoryTransport.createPair();

        broker.handlePublish(new WampMessage.Publish(1L,
                Map.of("retain", true, "exclude_me", false),
                "topic.retained", List.of("data")), pubPair[0], 10);

        broker.clearRetainedEvents();
        assertThat(broker.getRetainedEvent("topic.retained")).isNull();
    }
}
