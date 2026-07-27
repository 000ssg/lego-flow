package ssg.legoflow.mqtt.topic;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TopicTree}.
 *
 * @since 1.0.0
 */
class TopicTreeTest {

    @Test
    void testExactSubscriptionMatching() {
        // Given: exact subscription
        var tree = new TopicTree<String>();
        tree.subscribe("a/b/c", "sub1");

        // When: query
        Set<String> result = tree.getMatchingSubscribers("a/b/c");

        // Then: subscriber found
        assertThat(result).containsExactly("sub1");
    }

    @Test
    void testNoMatchReturnsEmpty() {
        // Given: subscription on one topic
        var tree = new TopicTree<String>();
        tree.subscribe("a/b", "sub1");

        // When: query different topic
        Set<String> result = tree.getMatchingSubscribers("x/y");

        // Then: empty
        assertThat(result).isEmpty();
    }

    @Test
    void testSingleLevelWildcardMatching() {
        // Given: wildcard subscription
        var tree = new TopicTree<String>();
        tree.subscribe("a/+/c", "sub1");

        // When: query matching topics
        Set<String> match = tree.getMatchingSubscribers("a/b/c");
        Set<String> noMatch = tree.getMatchingSubscribers("a/b/d");

        // Then: wildcard matches single level
        assertThat(match).containsExactly("sub1");
        assertThat(noMatch).isEmpty();
    }

    @Test
    void testMultiLevelWildcardMatching() {
        // Given: multi-level wildcard
        var tree = new TopicTree<String>();
        tree.subscribe("a/#", "sub1");

        // When: query various depths
        Set<String> r1 = tree.getMatchingSubscribers("a");
        Set<String> r2 = tree.getMatchingSubscribers("a/b");
        Set<String> r3 = tree.getMatchingSubscribers("a/b/c/d");

        // Then: all match
        assertThat(r1).containsExactly("sub1");
        assertThat(r2).containsExactly("sub1");
        assertThat(r3).containsExactly("sub1");
    }

    @Test
    void testMultipleSubscribersOnSameTopic() {
        // Given: two subscribers on same filter
        var tree = new TopicTree<String>();
        tree.subscribe("a/b", "sub1");
        tree.subscribe("a/b", "sub2");

        // When: query
        Set<String> result = tree.getMatchingSubscribers("a/b");

        // Then: both found
        assertThat(result).containsExactlyInAnyOrder("sub1", "sub2");
    }

    @Test
    void testOverlappingWildcards() {
        // Given: overlapping subscriptions
        var tree = new TopicTree<String>();
        tree.subscribe("a/+", "sub1");
        tree.subscribe("a/#", "sub2");
        tree.subscribe("a/b", "sub3");

        // When: query
        Set<String> result = tree.getMatchingSubscribers("a/b");

        // Then: all three match
        assertThat(result).containsExactlyInAnyOrder("sub1", "sub2", "sub3");
    }

    @Test
    void testUnsubscribe() {
        // Given: subscribed then unsubscribed
        var tree = new TopicTree<String>();
        tree.subscribe("a/b", "sub1");
        tree.unsubscribe("a/b", "sub1");

        // When: query
        Set<String> result = tree.getMatchingSubscribers("a/b");

        // Then: empty
        assertThat(result).isEmpty();
    }

    @Test
    void testUnsubscribeNonExistentIsNoop() {
        // Given: empty tree
        var tree = new TopicTree<String>();

        // When: unsubscribe non-existent
        tree.unsubscribe("a/b", "nobody");

        // Then: no error
        assertThat(tree.subscriberCount()).isEqualTo(0);
    }

    @Test
    void testSubscriberCount() {
        // Given: several subscriptions
        var tree = new TopicTree<String>();
        tree.subscribe("a", "s1");
        tree.subscribe("b", "s2");
        tree.subscribe("c", "s1"); // same subscriber, different topic

        // Then: unique count is 2
        assertThat(tree.subscriberCount()).isEqualTo(2);
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        // Given: concurrent subscribe and query
        var tree = new TopicTree<String>();
        int threads = 10;
        int opsPerThread = 100;
        var latch = new CountDownLatch(threads);
        var errors = ConcurrentHashMap.newKeySet();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                int threadId = t;
                exec.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            String filter = "topic/" + threadId + "/" + i;
                            tree.subscribe(filter, "sub-" + threadId);
                            tree.getMatchingSubscribers(filter);
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await();

        // Then: no errors
        assertThat(errors).isEmpty();
        assertThat(tree.subscriberCount()).isGreaterThan(0);
    }
}
