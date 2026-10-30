package ssg.legoflow.messaging.mqtt.protocol;

import java.util.List;

/**
 * MQTT SUBSCRIBE packet requesting topic subscriptions.
 *
 * @param packetId      the packet identifier
 * @param subscriptions the list of topic subscriptions
 * @param properties    MQTT 5.0 properties
 * @since 0.1.0
 */
public record SubscribePacket(
        int packetId,
        List<TopicSubscription> subscriptions,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.SUBSCRIBE;
    }
}
