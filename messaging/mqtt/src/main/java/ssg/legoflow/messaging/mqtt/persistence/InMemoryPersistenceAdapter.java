package ssg.legoflow.messaging.mqtt.persistence;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * In-memory reference implementation of {@link MqttPersistenceAdapter}.
 *
 * <p>Uses ConcurrentHashMap and ConcurrentLinkedQueue for thread-safe
 * storage. Data is lost on process exit — suitable for testing and
 * development. Production deployments should use a database-backed
 * implementation.</p>
 *
 * @since 0.2.0
 */
public final class InMemoryPersistenceAdapter implements MqttPersistenceAdapter {

    private final Map<String, MqttSessionState> sessions = new ConcurrentHashMap<>();
    private final Map<String, MqttRetainedState> retained = new ConcurrentHashMap<>();

    @Override
    public void saveSession(String clientId, boolean cleanSession, long expiryInterval,
                            List<MqttSubscriptionData> subscriptions,
                            String willTopic, byte[] willPayload, int willQos, boolean willRetain) {
        if (cleanSession) {
            sessions.remove(clientId);
            return;
        }
        sessions.compute(clientId, (key, existing) ->
                new MqttSessionState(expiryInterval,
                        new ArrayList<>(subscriptions),
                        willTopic, willPayload == null ? null : willPayload.clone(),
                        willQos, willRetain));
    }

    @Override
    public Optional<MqttSessionData> loadSession(String clientId) {
        MqttSessionState state = sessions.get(clientId);
        if (state == null) {
            return Optional.empty();
        }
        return Optional.of(new MqttSessionData(
                false, // persistent sessions are always cleanSession=false
                state.expiryInterval,
                state.subscriptions,
                state.willTopic,
                state.willPayload == null ? null : state.willPayload.clone(),
                state.willQos,
                state.willRetain));
    }

    @Override
    public void removeSession(String clientId) {
        sessions.remove(clientId);
    }

    @Override
    public void queueMessage(String clientId, String topic, ByteBuffer payload, int qos, boolean retain) {
        MqttSessionState state = sessions.computeIfAbsent(clientId,
                key -> new MqttSessionState(0, Collections.emptyList(),
                        null, null, 0, false));
        // Copy payload bytes to avoid buffer mutation
        byte[] bytes;
        if (payload.hasArray()) {
            bytes = payload.array();
        } else {
            bytes = new byte[payload.remaining()];
            payload.duplicate().get(bytes);
        }
        state.queuedMessages.add(new MqttQueuedMessage(topic, ByteBuffer.wrap(bytes), qos, retain));
    }

    @Override
    public List<MqttQueuedMessage> drainMessages(String clientId) {
        MqttSessionState state = sessions.get(clientId);
        if (state == null) {
            return Collections.emptyList();
        }
        List<MqttQueuedMessage> result = new ArrayList<>(state.queuedMessages);
        state.queuedMessages.clear();
        return result;
    }

    @Override
    public void saveRetained(String topic, byte[] payload, int qos) {
        if (payload == null || payload.length == 0) {
            retained.remove(topic);
        } else {
            retained.put(topic, new MqttRetainedState(payload.clone(), qos));
        }
    }

    @Override
    public Optional<byte[]> loadRetained(String topic) {
        MqttRetainedState state = retained.get(topic);
        return state == null ? Optional.empty()
                : Optional.of(state.payload.clone());
    }

    @Override
    public List<MqttRetainedMessage> loadAllRetained() {
        return retained.entrySet().stream()
                .map(e -> new MqttRetainedMessage(e.getKey(),
                        e.getValue().payload.clone(), e.getValue().qos))
                .collect(Collectors.toList());
    }

    @Override
    public void removeRetained(String topic) {
        retained.remove(topic);
    }

    @Override
    public void clearAll() {
        sessions.clear();
        retained.clear();
    }

    @Override
    public void close() {
        clearAll();
    }

    private static final class MqttSessionState {
        final long expiryInterval;
        final List<MqttSubscriptionData> subscriptions;
        final String willTopic;
        final byte[] willPayload;
        final int willQos;
        final boolean willRetain;
        final ConcurrentLinkedQueue<MqttQueuedMessage> queuedMessages = new ConcurrentLinkedQueue<>();

        MqttSessionState(long expiryInterval, List<MqttSubscriptionData> subscriptions,
                         String willTopic, byte[] willPayload, int willQos, boolean willRetain) {
            this.expiryInterval = expiryInterval;
            this.subscriptions = subscriptions;
            this.willTopic = willTopic;
            this.willPayload = willPayload;
            this.willQos = willQos;
            this.willRetain = willRetain;
        }
    }

    private static final class MqttRetainedState {
        final byte[] payload;
        final int qos;

        MqttRetainedState(byte[] payload, int qos) {
            this.payload = payload;
            this.qos = qos;
        }
    }
}
