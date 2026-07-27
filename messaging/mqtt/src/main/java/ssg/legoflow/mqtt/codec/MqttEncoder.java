package ssg.legoflow.mqtt.codec;

import ssg.legoflow.mqtt.protocol.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Encodes individual MQTT packet types into {@link ByteBuffer}.
 *
 * <p>Handles the variable header and payload encoding for each packet type.
 * The fixed header (packet type + remaining length) is added by {@link MqttCodec}.
 *
 * @since 1.0.0
 */
public final class MqttEncoder {

    private MqttEncoder() {
    }

    /**
     * Encodes a CONNECT packet variable header and payload.
     *
     * @param packet the CONNECT packet
     * @return encoded bytes (variable header + payload)
     */
    public static ByteBuffer encodeConnect(ConnectPacket packet) {
        var buf = ByteBuffer.allocate(4096);

        // Variable header: Protocol Name
        encodeUtf8(buf, packet.version().protocolName());
        // Protocol Level
        buf.put((byte) packet.version().protocolLevel());

        // Connect Flags
        int flags = 0;
        if (packet.cleanSession()) flags |= 0x02;
        if (packet.will() != null) {
            flags |= 0x04;
            flags |= (packet.will().qos().value() << 3);
            if (packet.will().retain()) flags |= 0x20;
        }
        if (packet.password() != null) flags |= 0x40;
        if (packet.username() != null) flags |= 0x80;
        buf.put((byte) flags);

        // Keep Alive
        buf.putShort((short) packet.keepAlive());

        // MQTT 5.0 properties
        if (packet.version() == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }

        // Payload
        encodeUtf8(buf, packet.clientId());
        if (packet.will() != null) {
            if (packet.version() == MqttVersion.V5_0) {
                encodeProperties(buf, packet.will().properties());
            }
            encodeUtf8(buf, packet.will().topic());
            encodeBinary(buf, packet.will().payload());
        }
        if (packet.username() != null) {
            encodeUtf8(buf, packet.username());
        }
        if (packet.password() != null) {
            encodeUtf8(buf, packet.password());
        }

        buf.flip();
        return buf;
    }

    /**
     * Encodes a CONNACK packet variable header.
     *
     * @param packet  the CONNACK packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeConnAck(ConnAckPacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(256);
        buf.put((byte) (packet.sessionPresent() ? 0x01 : 0x00));
        buf.put((byte) packet.returnCode().value());
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes a PUBLISH packet variable header and payload.
     *
     * @param packet  the PUBLISH packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodePublish(PublishPacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(packet.payload().length + 256);
        encodeUtf8(buf, packet.topic());
        if (packet.qos() != QoS.AT_MOST_ONCE) {
            buf.putShort((short) packet.packetId());
        }
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }
        buf.put(packet.payload());
        buf.flip();
        return buf;
    }

    /**
     * Encodes a PUBACK, PUBREC, PUBREL, or PUBCOMP packet.
     *
     * @param packetId   the packet identifier
     * @param reasonCode the reason code
     * @param properties the properties
     * @param version    the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeAck(int packetId, ReasonCode reasonCode,
                                       MqttProperties properties, MqttVersion version) {
        var buf = ByteBuffer.allocate(256);
        buf.putShort((short) packetId);
        if (version == MqttVersion.V5_0) {
            buf.put((byte) reasonCode.value());
            encodeProperties(buf, properties);
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes a SUBSCRIBE packet.
     *
     * @param packet  the SUBSCRIBE packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeSubscribe(SubscribePacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(4096);
        buf.putShort((short) packet.packetId());
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }
        for (var sub : packet.subscriptions()) {
            encodeUtf8(buf, sub.topicFilter());
            int options = sub.qos().value();
            if (version == MqttVersion.V5_0) {
                if (sub.noLocal()) options |= 0x04;
                if (sub.retainAsPublished()) options |= 0x08;
                options |= (sub.retainHandling().value() << 4);
            }
            buf.put((byte) options);
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes a SUBACK packet.
     *
     * @param packet  the SUBACK packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeSubAck(SubAckPacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(256);
        buf.putShort((short) packet.packetId());
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }
        for (var code : packet.reasonCodes()) {
            buf.put((byte) code.value());
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes an UNSUBSCRIBE packet.
     *
     * @param packet  the UNSUBSCRIBE packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeUnsubscribe(UnsubscribePacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(4096);
        buf.putShort((short) packet.packetId());
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
        }
        for (var topic : packet.topics()) {
            encodeUtf8(buf, topic);
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes an UNSUBACK packet.
     *
     * @param packet  the UNSUBACK packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeUnsubAck(UnsubAckPacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(256);
        buf.putShort((short) packet.packetId());
        if (version == MqttVersion.V5_0) {
            encodeProperties(buf, packet.properties());
            for (var code : packet.reasonCodes()) {
                buf.put((byte) code.value());
            }
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes a DISCONNECT packet.
     *
     * @param packet  the DISCONNECT packet
     * @param version the MQTT version
     * @return encoded bytes
     */
    public static ByteBuffer encodeDisconnect(DisconnectPacket packet, MqttVersion version) {
        var buf = ByteBuffer.allocate(256);
        if (version == MqttVersion.V5_0) {
            buf.put((byte) packet.reasonCode().value());
            encodeProperties(buf, packet.properties());
        }
        buf.flip();
        return buf;
    }

    /**
     * Encodes an AUTH packet (MQTT 5.0 only).
     *
     * @param packet the AUTH packet
     * @return encoded bytes
     */
    public static ByteBuffer encodeAuth(AuthPacket packet) {
        var buf = ByteBuffer.allocate(256);
        buf.put((byte) packet.reasonCode().value());
        encodeProperties(buf, packet.properties());
        buf.flip();
        return buf;
    }

    // --- Helpers ---

    static void encodeUtf8(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.putShort((short) bytes.length);
        buf.put(bytes);
    }

    static void encodeBinary(ByteBuffer buf, byte[] data) {
        buf.putShort((short) data.length);
        buf.put(data);
    }

    static void encodeProperties(ByteBuffer buf, MqttProperties properties) {
        if (properties == null || properties.isEmpty()) {
            buf.put((byte) 0);
            return;
        }
        ByteBuffer encoded = properties.encode();
        encodeVariableByteInteger(buf, encoded.remaining());
        buf.put(encoded);
    }

    /**
     * Encodes a variable byte integer (MQTT remaining length encoding).
     *
     * @param buf   the target buffer
     * @param value the value to encode (0 to 268,435,455)
     */
    static void encodeVariableByteInteger(ByteBuffer buf, int value) {
        do {
            int encodedByte = value % 128;
            value /= 128;
            if (value > 0) {
                encodedByte |= 0x80;
            }
            buf.put((byte) encodedByte);
        } while (value > 0);
    }
}
