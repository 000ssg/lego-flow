package ssg.legoflow.mqtt.protocol;

/**
 * MQTT DISCONNECT packet indicating a clean disconnection.
 *
 * @param reasonCode the reason for disconnection (MQTT 5.0)
 * @param properties MQTT 5.0 properties
 * @since 1.0.0
 */
public record DisconnectPacket(
        ReasonCode reasonCode,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.DISCONNECT;
    }
}
