package ssg.legoflow.messaging.mqtt.protocol;

/**
 * MQTT PUBLISH packet carrying an application message.
 *
 * @param topic      the topic name
 * @param payload    the message payload
 * @param qos        the QoS level
 * @param retain     whether the message should be retained
 * @param dup        whether this is a re-delivery
 * @param packetId   the packet identifier (0 for QoS 0)
 * @param properties MQTT 5.0 properties
 * @since 0.1.0
 */
public record PublishPacket(
        String topic,
        byte[] payload,
        QoS qos,
        boolean retain,
        boolean dup,
        int packetId,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.PUBLISH;
    }
}
