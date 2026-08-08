package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.protocol.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MqttSession}.
 *
 * @since 0.1.0
 */
class MqttSessionTest {

    @Test
    void testCleanSession() {
        // Given: clean session
        var session = new MqttSession("client-1", true, 100);

        // Then: properties
        assertThat(session.clientId()).isEqualTo("client-1");
        assertThat(session.isCleanSession()).isTrue();
        assertThat(session.isConnected()).isFalse();
    }

    @Test
    void testPersistentSession() {
        // Given: persistent session
        var session = new MqttSession("client-2", false, 100);

        // Then:
        assertThat(session.isCleanSession()).isFalse();
    }

    @Test
    void testAddAndRemoveSubscription() {
        // Given: session with subscription
        var session = new MqttSession("s", true, 100);
        session.addSubscription(new TopicSubscription("a/b", QoS.AT_LEAST_ONCE));

        // Then: subscription present
        assertThat(session.getSubscriptions()).containsKey("a/b");

        // When: remove
        session.removeSubscription("a/b");

        // Then: removed
        assertThat(session.getSubscriptions()).isEmpty();
    }

    @Test
    void testQueueAndDrainMessages() {
        // Given: session with queued messages
        var session = new MqttSession("q", false, 100);
        var msg1 = new PublishPacket("t", "1".getBytes(), QoS.AT_LEAST_ONCE,
                false, false, 1, new MqttProperties());
        var msg2 = new PublishPacket("t", "2".getBytes(), QoS.AT_LEAST_ONCE,
                false, false, 2, new MqttProperties());

        session.queueMessage(msg1);
        session.queueMessage(msg2);

        // Then: queue size
        assertThat(session.queuedMessageCount()).isEqualTo(2);

        // When: drain
        var drained = session.drainQueuedMessages();

        // Then: messages returned, queue empty
        assertThat(drained).hasSize(2);
        assertThat(session.queuedMessageCount()).isEqualTo(0);
    }

    @Test
    void testQueueMaxLimit() {
        // Given: session with max 2 queued messages
        var session = new MqttSession("limit", false, 2);
        for (int i = 0; i < 5; i++) {
            session.queueMessage(new PublishPacket("t", String.valueOf(i).getBytes(),
                    QoS.AT_LEAST_ONCE, false, false, i, new MqttProperties()));
        }

        // Then: only 2 queued
        assertThat(session.queuedMessageCount()).isEqualTo(2);
    }

    @Test
    void testInflightMessages() {
        // Given: session tracking in-flight messages
        var session = new MqttSession("inf", true, 100);
        var msg = new PublishPacket("t", "data".getBytes(), QoS.AT_LEAST_ONCE,
                false, false, 10, new MqttProperties());

        session.addInflightMessage(10, msg);

        // Then: in-flight count
        assertThat(session.inflightCount()).isEqualTo(1);

        // When: remove
        var removed = session.removeInflightMessage(10);

        // Then: removed
        assertThat(removed).isNotNull();
        assertThat(session.inflightCount()).isEqualTo(0);
    }

    @Test
    void testNextPacketId() {
        // Given: session
        var session = new MqttSession("pid", true, 100);

        // When: generate IDs
        int id1 = session.nextPacketId();
        int id2 = session.nextPacketId();

        // Then: sequential
        assertThat(id1).isEqualTo(1);
        assertThat(id2).isEqualTo(2);
    }

    @Test
    void testClearSession() {
        // Given: session with state
        var session = new MqttSession("clear", false, 100);
        session.addSubscription(new TopicSubscription("x", QoS.AT_LEAST_ONCE));
        session.queueMessage(new PublishPacket("t", "m".getBytes(), QoS.AT_LEAST_ONCE,
                false, false, 1, new MqttProperties()));

        // When: clear
        session.clear();

        // Then: all state removed
        assertThat(session.getSubscriptions()).isEmpty();
        assertThat(session.queuedMessageCount()).isEqualTo(0);
        assertThat(session.inflightCount()).isEqualTo(0);
    }
}
