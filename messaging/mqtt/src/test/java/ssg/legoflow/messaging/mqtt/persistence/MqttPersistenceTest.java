package ssg.legoflow.messaging.mqtt.persistence;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.mqtt.persistence.MqttPersistenceAdapter.MqttQueuedMessage;
import ssg.legoflow.messaging.mqtt.persistence.MqttPersistenceAdapter.MqttRetainedMessage;
import ssg.legoflow.messaging.mqtt.persistence.MqttPersistenceAdapter.MqttSessionData;
import ssg.legoflow.messaging.mqtt.persistence.MqttPersistenceAdapter.MqttSubscriptionData;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests for InMemoryPersistenceAdapter covering all adapter methods. */
class MqttPersistenceTest {

    private final MqttPersistenceAdapter adapter = new InMemoryPersistenceAdapter();

    @Test
    void cleanSessionRemovesExistingSession() {
        adapter.saveSession("c1", false, 0, List.of(new MqttSubscriptionData("t/#", 1)), null, null, 0, false);
        adapter.saveSession("c1", true, 0, List.of(), null, null, 0, false);
        assertTrue(adapter.loadSession("c1").isEmpty());
    }

    @Test
    void saveAndLoadSessionRoundTrip() {
        adapter.saveSession("c1", false, 300, List.of(
                new MqttSubscriptionData("a/b", 1),
                new MqttSubscriptionData("c/#", 2)),
                "will/t", "will".getBytes(), 1, true);
        MqttSessionData s = adapter.loadSession("c1").orElseThrow();
        assertFalse(s.cleanSession());
        assertEquals(300, s.expiryInterval());
        assertEquals(2, s.subscriptions().size());
        assertEquals("a/b", s.subscriptions().get(0).topicFilter());
        assertEquals(1, s.subscriptions().get(0).qos());
        assertEquals("will/t", s.willTopic());
        assertArrayEquals("will".getBytes(), s.willPayload());
        assertEquals(1, s.willQos());
        assertTrue(s.willRetain());
    }

    @Test
    void loadNonExistentSessionReturnsEmpty() {
        assertTrue(adapter.loadSession("nope").isEmpty());
    }

    @Test
    void removeSession() {
        adapter.saveSession("c1", false, 0, List.of(), null, null, 0, false);
        assertTrue(adapter.loadSession("c1").isPresent());
        adapter.removeSession("c1");
        assertTrue(adapter.loadSession("c1").isEmpty());
    }

    @Test
    void queueAndDrainMessages() {
        adapter.saveSession("c1", false, 0, List.of(), null, null, 0, false);
        adapter.queueMessage("c1", "t/1", ByteBuffer.wrap("hello".getBytes()), 1, false);
        adapter.queueMessage("c1", "t/2", ByteBuffer.wrap("world".getBytes()), 0, true);
        List<MqttQueuedMessage> msgs = adapter.drainMessages("c1");
        assertEquals(2, msgs.size());
        assertEquals("t/1", msgs.get(0).topic());
        assertArrayEquals("hello".getBytes(), msgs.get(0).payload().array());
        assertEquals(1, msgs.get(0).qos());
        assertFalse(msgs.get(0).retain());
        assertEquals("t/2", msgs.get(1).topic());
        assertArrayEquals("world".getBytes(), msgs.get(1).payload().array());
        assertEquals(0, msgs.get(1).qos());
        assertTrue(msgs.get(1).retain());
        assertTrue(adapter.drainMessages("c1").isEmpty());
    }

    @Test
    void drainNonExistentSessionReturnsEmpty() {
        assertTrue(adapter.drainMessages("ghost").isEmpty());
    }

    @Test
    void queueMessageCreatesSessionIfAbsent() {
        adapter.queueMessage("c2", "t", ByteBuffer.wrap("x".getBytes()), 0, false);
        assertEquals(1, adapter.drainMessages("c2").size());
    }

    @Test
    void saveAndLoadRetainedMessage() {
        adapter.saveRetained("a/b/c", "retained".getBytes(), 1);
        Optional<byte[]> payload = adapter.loadRetained("a/b/c");
        assertTrue(payload.isPresent());
        assertArrayEquals("retained".getBytes(), payload.get());
    }

    @Test
    void loadNonExistentRetainedReturnsEmpty() {
        assertTrue(adapter.loadRetained("nope").isEmpty());
    }

    @Test
    void saveRetainedWithEmptyPayloadRemoves() {
        adapter.saveRetained("t", "data".getBytes(), 0);
        assertTrue(adapter.loadRetained("t").isPresent());
        adapter.saveRetained("t", new byte[0], 0);
        assertTrue(adapter.loadRetained("t").isEmpty());
    }

    @Test
    void saveRetainedWithNullPayloadRemoves() {
        adapter.saveRetained("t", "data".getBytes(), 0);
        assertTrue(adapter.loadRetained("t").isPresent());
        adapter.saveRetained("t", null, 0);
        assertTrue(adapter.loadRetained("t").isEmpty());
    }

    @Test
    void loadAllRetained() {
        adapter.saveRetained("a", "x".getBytes(), 0);
        adapter.saveRetained("b", "y".getBytes(), 1);
        assertEquals(2, adapter.loadAllRetained().size());
    }

    @Test
    void removeRetained() {
        adapter.saveRetained("t", "x".getBytes(), 0);
        adapter.removeRetained("t");
        assertTrue(adapter.loadRetained("t").isEmpty());
    }

    @Test
    void clearAll() {
        adapter.saveSession("c1", false, 0, List.of(), null, null, 0, false);
        adapter.saveRetained("t", "x".getBytes(), 0);
        adapter.clearAll();
        assertTrue(adapter.loadSession("c1").isEmpty());
        assertTrue(adapter.loadRetained("t").isEmpty());
    }

    @Test
    void closeClearsAll() {
        adapter.saveSession("c1", false, 0, List.of(), null, null, 0, false);
        adapter.saveRetained("t", "x".getBytes(), 0);
        adapter.close();
        assertTrue(adapter.loadSession("c1").isEmpty());
        assertTrue(adapter.loadRetained("t").isEmpty());
    }

    @Test
    void payloadIsClonedOnSave() {
        byte[] payload = "original".getBytes();
        adapter.saveRetained("t", payload, 0);
        payload[0] = 'Z';
        Optional<byte[]> loaded = adapter.loadRetained("t");
        assertTrue(loaded.isPresent());
        assertEquals('o', loaded.get()[0]);
    }

    @Test
    void willPayloadIsClonedOnSessionSave() {
        byte[] will = "will".getBytes();
        adapter.saveSession("c", false, 0, List.of(), "wt", will, 1, false);
        will[0] = 'X';
        MqttSessionData data = adapter.loadSession("c").orElseThrow();
        assertEquals('w', data.willPayload()[0]);
    }
}
