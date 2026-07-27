package ssg.legoflow.mqtt.protocol;

/**
 * Base sealed interface for all MQTT control packets.
 *
 * <p>Each permitted implementation corresponds to one of the 15 MQTT packet types.
 *
 * @since 1.0.0
 */
public sealed interface MqttPacket
        permits ConnectPacket, ConnAckPacket, PublishPacket,
                PubAckPacket, PubRecPacket, PubRelPacket, PubCompPacket,
                SubscribePacket, SubAckPacket, UnsubscribePacket, UnsubAckPacket,
                PingReqPacket, PingRespPacket, DisconnectPacket, AuthPacket {

    /**
     * Returns the MQTT control packet type.
     *
     * @return the packet type
     */
    MqttPacketType type();
}
