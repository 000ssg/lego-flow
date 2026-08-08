package ssg.legoflow.mqtt.protocol;

/**
 * MQTT PUBACK packet acknowledging a QoS 1 PUBLISH.
 *
 * @param packetId   the packet identifier being acknowledged
 * @param reasonCode the reason code (MQTT 5.0)
 * @param properties MQTT 5.0 properties
 * @since 0.1.0
 */
public record PubAckPacket(
        int packetId,
        ReasonCode reasonCode,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.PUBACK;
    }
}
