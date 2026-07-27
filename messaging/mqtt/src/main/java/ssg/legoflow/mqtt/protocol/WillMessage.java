package ssg.legoflow.mqtt.protocol;

/**
 * MQTT last will and testament message.
 *
 * <p>Published by the broker on behalf of a client that disconnects unexpectedly.
 *
 * @param topic      the will topic
 * @param payload    the will payload
 * @param qos        the will QoS level
 * @param retain     whether the will message should be retained
 * @param properties MQTT 5.0 will properties (may be empty)
 * @since 1.0.0
 */
public record WillMessage(
        String topic,
        byte[] payload,
        QoS qos,
        boolean retain,
        MqttProperties properties
) {

    /**
     * Creates a simple will message without MQTT 5.0 properties.
     *
     * @param topic   the will topic
     * @param payload the will payload
     * @param qos     the will QoS level
     * @param retain  whether the will message should be retained
     */
    public WillMessage(String topic, byte[] payload, QoS qos, boolean retain) {
        this(topic, payload, qos, retain, new MqttProperties());
    }
}
