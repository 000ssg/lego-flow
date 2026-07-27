package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.protocol.MqttProperties;
import ssg.legoflow.mqtt.protocol.PublishPacket;
import ssg.legoflow.mqtt.protocol.QoS;
import ssg.legoflow.mqtt.topic.TopicFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe retained message storage.
 *
 * <p>Stores the last retained message for each topic. A retained message with
 * an empty payload removes the stored message.
 *
 * @since 1.0.0
 */
public final class RetainStore {

    private final ConcurrentHashMap<String, PublishPacket> store = new ConcurrentHashMap<>();

    /**
     * Stores a retained message for the given topic.
     *
     * <p>An empty payload removes any existing retained message.
     *
     * @param topic   the topic
     * @param payload the message payload
     */
    public void put(String topic, byte[] payload) {
        if (payload == null || payload.length == 0) {
            store.remove(topic);
        } else {
            store.put(topic, new PublishPacket(topic, payload, QoS.AT_MOST_ONCE,
                    true, false, 0, new MqttProperties()));
        }
    }

    /**
     * Returns the retained message for the given topic, or {@code null} if none.
     *
     * @param topic the exact topic name
     * @return the retained PUBLISH packet, or {@code null}
     */
    public PublishPacket get(String topic) {
        return store.get(topic);
    }

    /**
     * Removes the retained message for the given topic.
     *
     * @param topic the topic
     */
    public void remove(String topic) {
        store.remove(topic);
    }

    /**
     * Returns all retained messages matching the given topic filter.
     *
     * @param topicFilter the topic filter (may contain wildcards)
     * @return the list of matching retained PUBLISH packets
     */
    public List<PublishPacket> getMatching(String topicFilter) {
        TopicFilter filter = new TopicFilter(topicFilter);
        List<PublishPacket> result = new ArrayList<>();
        for (var entry : store.entrySet()) {
            if (filter.matches(entry.getKey())) {
                result.add(entry.getValue());
            }
        }
        return result;
    }

    /**
     * Returns the number of retained messages.
     *
     * @return the count
     */
    public int size() {
        return store.size();
    }

    /**
     * Removes all retained messages.
     */
    public void clear() {
        store.clear();
    }
}
