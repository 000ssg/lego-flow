package ssg.legoflow.mqtt.protocol;

/**
 * MQTT CONNACK packet sent by the server in response to a CONNECT.
 *
 * @param sessionPresent whether a session already exists for the client
 * @param returnCode     the connection result code
 * @param properties     MQTT 5.0 properties
 * @since 0.1.0
 */
public record ConnAckPacket(
        boolean sessionPresent,
        ConnectReturnCode returnCode,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.CONNACK;
    }
}
