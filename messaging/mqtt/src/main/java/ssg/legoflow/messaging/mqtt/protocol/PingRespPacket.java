package ssg.legoflow.messaging.mqtt.protocol;

/**
 * MQTT PINGRESP packet sent by the server in response to a PINGREQ.
 *
 * @since 0.1.0
 */
public record PingRespPacket() implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.PINGRESP;
    }
}
