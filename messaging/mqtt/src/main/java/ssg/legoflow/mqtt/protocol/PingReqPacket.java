package ssg.legoflow.mqtt.protocol;

/**
 * MQTT PINGREQ packet sent by the client to maintain the connection.
 *
 * @since 1.0.0
 */
public record PingReqPacket() implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.PINGREQ;
    }
}
