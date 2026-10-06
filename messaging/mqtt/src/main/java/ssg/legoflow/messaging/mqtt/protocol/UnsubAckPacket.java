package ssg.legoflow.messaging.mqtt.protocol;

import java.util.List;

/**
 * MQTT UNSUBACK packet acknowledging an UNSUBSCRIBE request.
 *
 * @param packetId    the packet identifier matching the UNSUBSCRIBE
 * @param reasonCodes the list of reason codes (MQTT 5.0), one per topic
 * @param properties  MQTT 5.0 properties
 * @since 0.1.0
 */
public record UnsubAckPacket(
        int packetId,
        List<ReasonCode> reasonCodes,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.UNSUBACK;
    }
}
