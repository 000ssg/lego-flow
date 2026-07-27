package ssg.legoflow.mqtt.protocol;

/**
 * MQTT PUBREL packet — second step in QoS 2 flow.
 *
 * @param packetId   the packet identifier
 * @param reasonCode the reason code (MQTT 5.0)
 * @param properties MQTT 5.0 properties
 * @since 1.0.0
 */
public record PubRelPacket(
        int packetId,
        ReasonCode reasonCode,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.PUBREL;
    }
}
