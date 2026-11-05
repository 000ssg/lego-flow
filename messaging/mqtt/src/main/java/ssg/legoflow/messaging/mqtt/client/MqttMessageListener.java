package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.protocol.QoS;

/**
 * Functional interface for receiving MQTT messages on subscribed topics.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface MqttMessageListener {

    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param topic   the topic the message was published to
     * @param payload the message payload
     * @param qos     the QoS level of the message
     * @param retain  whether this is a retained message
     */
    void onMessage(String topic, byte[] payload, QoS qos, boolean retain);
}
