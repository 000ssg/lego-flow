package ssg.legoflow.mqtt.protocol;

/**
 * MQTT CONNECT packet sent by a client to initiate a connection.
 *
 * @param version      the MQTT protocol version
 * @param clientId     the client identifier
 * @param cleanSession whether to start a clean session
 * @param keepAlive    the keep-alive interval in seconds
 * @param username     the username (may be {@code null})
 * @param password     the password (may be {@code null})
 * @param will         the will message (may be {@code null})
 * @param properties   MQTT 5.0 properties
 * @since 1.0.0
 */
public record ConnectPacket(
        MqttVersion version,
        String clientId,
        boolean cleanSession,
        int keepAlive,
        String username,
        String password,
        WillMessage will,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.CONNECT;
    }
}
