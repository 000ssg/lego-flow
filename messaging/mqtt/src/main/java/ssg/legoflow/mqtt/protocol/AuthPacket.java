package ssg.legoflow.mqtt.protocol;

/**
 * MQTT AUTH packet for extended authentication exchanges (MQTT 5.0 only).
 *
 * @param reasonCode the authentication reason code
 * @param properties MQTT 5.0 properties
 * @since 0.1.0
 */
public record AuthPacket(
        ReasonCode reasonCode,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.AUTH;
    }
}
