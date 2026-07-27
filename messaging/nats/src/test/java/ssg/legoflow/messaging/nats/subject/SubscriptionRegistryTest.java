package ssg.legoflow.messaging.nats.subject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SubscriptionRegistry}.
 */
class SubscriptionRegistryTest {

    @Test
    void testSubscribeAndMatchExact() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.bar", "sub1");

        assertThat(registry.match("foo.bar")).containsExactly("sub1");
        assertThat(registry.match("foo.baz")).isEmpty();
    }

    @Test
    void testSubscribeAndMatchWildcard() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.*", "sub1");

        assertThat(registry.match("foo.bar")).containsExactly("sub1");
        assertThat(registry.match("foo.baz")).containsExactly("sub1");
        assertThat(registry.match("bar.baz")).isEmpty();
    }

    @Test
    void testSubscribeAndMatchFullWildcard() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.>", "sub1");

        assertThat(registry.match("foo.bar")).containsExactly("sub1");
        assertThat(registry.match("foo.bar.baz")).containsExactly("sub1");
        assertThat(registry.match("foo")).isEmpty();
    }

    @Test
    void testMultipleSubscribers() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.bar", "sub1");
        registry.subscribe("foo.bar", "sub2");

        assertThat(registry.match("foo.bar")).containsExactlyInAnyOrder("sub1", "sub2");
    }

    @Test
    void testMixedExactAndWildcard() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.bar", "exact");
        registry.subscribe("foo.*", "wildcard");

        var matches = registry.match("foo.bar");
        assertThat(matches).containsExactlyInAnyOrder("exact", "wildcard");
    }

    @Test
    void testUnsubscribe() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.bar", "sub1");
        assertThat(registry.unsubscribe("foo.bar", "sub1")).isTrue();
        assertThat(registry.match("foo.bar")).isEmpty();
    }

    @Test
    void testUnsubscribeNonExistent() {
        var registry = new SubscriptionRegistry<String>();
        assertThat(registry.unsubscribe("foo.bar", "sub1")).isFalse();
    }

    @Test
    void testUnsubscribeWildcard() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("foo.*", "sub1");
        assertThat(registry.unsubscribe("foo.*", "sub1")).isTrue();
        assertThat(registry.match("foo.bar")).isEmpty();
    }

    @Test
    void testSize() {
        var registry = new SubscriptionRegistry<String>();
        assertThat(registry.size()).isEqualTo(0);
        registry.subscribe("a", "1");
        registry.subscribe("b", "2");
        registry.subscribe("c.*", "3");
        assertThat(registry.size()).isEqualTo(3);
    }

    @Test
    void testClear() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("a", "1");
        registry.subscribe("b.*", "2");
        registry.clear();
        assertThat(registry.size()).isEqualTo(0);
        assertThat(registry.match("a")).isEmpty();
    }

    @Test
    void testPatterns() {
        var registry = new SubscriptionRegistry<String>();
        registry.subscribe("a.b", "1");
        registry.subscribe("c.>", "2");
        assertThat(registry.patterns()).containsExactlyInAnyOrder("a.b", "c.>");
    }
}
