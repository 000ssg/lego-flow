package ssg.legoflow.mqtt.protocol;

import java.util.List;

/**
 * MQTT SUBACK packet acknowledging a SUBSCRIBE request.
 *
 * @param packetId    the packet identifier matching the SUBSCRIBE
 * @param reasonCodes the list of reason codes, one per subscription
 * @param properties  MQTT 5.0 properties
 * @since 0.1.0
 */
public record SubAckPacket(
        int packetId,
        List<ReasonCode> reasonCodes,
        MqttProperties properties
) implements MqttPacket {

    @Override
    public MqttPacketType type() {
        return MqttPacketType.SUBACK;
    }
}
