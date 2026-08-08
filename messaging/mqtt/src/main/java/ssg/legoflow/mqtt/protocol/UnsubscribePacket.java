package ssg.legoflow.mqtt.protocol;

import java.util.List;

/**
 * MQTT UNSUBSCRIBE packet requesting removal of topic subscriptions.
 *
 * @param packetId   the packet identifier
 * @param topics     the list of topic filters to unsubscribe from
 * @param properties MQTT 5.0 properties
 * @since 0.1.0
 */
public record UnsubscribePacket(
        int packetId,
        List<String> topics,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.UNSUBSCRIBE;
    }
}
