package ssg.legoflow.xmpp.pubsub;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link PubSubSubscription}.
 *
 * @since 0.1.0
 */
class PubSubSubscriptionTest {

    @Test
    void testCreateSubscription() {
        var sub = new PubSubSubscription("node-1", "alice@example.com", "sub-1",
                PubSubSubscription.State.SUBSCRIBED);

        assertThat(sub.nodeId()).isEqualTo("node-1");
        assertThat(sub.jid()).isEqualTo("alice@example.com");
        assertThat(sub.subscriptionId()).isEqualTo("sub-1");
        assertThat(sub.state()).isEqualTo(PubSubSubscription.State.SUBSCRIBED);
    }

    @Test
    void testToXml() {
        var sub = new PubSubSubscription("node-1", "bob@example.com", "sub-2",
                PubSubSubscription.State.PENDING);
        String xml = sub.toXml();

        assertThat(xml).contains("node=\"node-1\"");
        assertThat(xml).contains("jid=\"bob@example.com\"");
        assertThat(xml).contains("subid=\"sub-2\"");
        assertThat(xml).contains("subscription=\"pending\"");
    }

    @Test
    void testAllStates() {
        for (var state : PubSubSubscription.State.values()) {
            assertThat(state).isNotNull();
        }
        assertThat(PubSubSubscription.State.values()).hasSize(4);
    }

    @Test
    void testNullNodeIdThrows() {
        assertThatThrownBy(() -> new PubSubSubscription(null, "jid", "sub", PubSubSubscription.State.NONE))
                .isInstanceOf(NullPointerException.class);
    }
}
