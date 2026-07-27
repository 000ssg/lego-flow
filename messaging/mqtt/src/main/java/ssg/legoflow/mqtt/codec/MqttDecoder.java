package ssg.legoflow.mqtt.codec;

import ssg.legoflow.mqtt.protocol.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes individual MQTT packet types from {@link ByteBuffer}.
 *
 * <p>Handles the variable header and payload decoding for each packet type.
 *
 * @since 1.0.0
 */
public final class MqttDecoder {

    private MqttDecoder() {
    }

    /**
     * Decodes a CONNECT packet from the given buffer.
     *
     * @param buf the buffer positioned at the variable header
     * @return the decoded CONNECT packet
     */
    public static ConnectPacket decodeConnect(ByteBuffer buf) {
        // Protocol Name
        String protocolName = decodeUtf8(buf);
        // Protocol Level
        int protocolLevel = buf.get() & 0xFF;
        MqttVersion version = MqttVersion.fromProtocolLevel(protocolLevel);

        // Connect Flags
        int flags = buf.get() & 0xFF;
        boolean cleanSession = (flags & 0x02) != 0;
        boolean hasWill = (flags & 0x04) != 0;
        QoS willQos = QoS.fromValue((flags >> 3) & 0x03);
        boolean willRetain = (flags & 0x20) != 0;
        boolean hasPassword = (flags & 0x40) != 0;
        boolean hasUsername = (flags & 0x80) != 0;

        // Keep Alive
        int keepAlive = buf.getShort() & 0xFFFF;

        // MQTT 5.0 properties
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
        }

        // Payload
        String clientId = decodeUtf8(buf);

        WillMessage will = null;
        if (hasWill) {
            MqttProperties willProps = new MqttProperties();
            if (version == MqttVersion.V5_0) {
                willProps = decodeProperties(buf);
            }
            String willTopic = decodeUtf8(buf);
            byte[] willPayload = decodeBinary(buf);
            will = new WillMessage(willTopic, willPayload, willQos, willRetain, willProps);
        }

        String username = hasUsername ? decodeUtf8(buf) : null;
        String password = hasPassword ? decodeUtf8(buf) : null;

        return new ConnectPacket(version, clientId, cleanSession, keepAlive,
                username, password, will, properties);
    }

    /**
     * Decodes a CONNACK packet from the given buffer.
     *
     * @param buf     the buffer positioned at the variable header
     * @param version the MQTT version
     * @return the decoded CONNACK packet
     */
    public static ConnAckPacket decodeConnAck(ByteBuffer buf, MqttVersion version) {
        int ackFlags = buf.get() & 0xFF;
        boolean sessionPresent = (ackFlags & 0x01) != 0;
        ConnectReturnCode returnCode = ConnectReturnCode.fromValue(buf.get() & 0xFF);
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0 && buf.hasRemaining()) {
            properties = decodeProperties(buf);
        }
        return new ConnAckPacket(sessionPresent, returnCode, properties);
    }

    /**
     * Decodes a PUBLISH packet from the given buffer.
     *
     * @param buf            the buffer positioned at the variable header
     * @param flags          the fixed header flags
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded PUBLISH packet
     */
    public static PublishPacket decodePublish(ByteBuffer buf, int flags,
                                             int remainingBytes, MqttVersion version) {
        boolean dup = (flags & 0x08) != 0;
        QoS qos = QoS.fromValue((flags >> 1) & 0x03);
        boolean retain = (flags & 0x01) != 0;

        int startPos = buf.position();
        String topic = decodeUtf8(buf);

        int packetId = 0;
        if (qos != QoS.AT_MOST_ONCE) {
            packetId = buf.getShort() & 0xFFFF;
        }

        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
        }

        int headerSize = buf.position() - startPos;
        int payloadSize = remainingBytes - headerSize;
        byte[] payload = new byte[payloadSize];
        buf.get(payload);

        return new PublishPacket(topic, payload, qos, retain, dup, packetId, properties);
    }

    /**
     * Decodes a PUBACK, PUBREC, PUBREL, or PUBCOMP from the given buffer.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return a three-element array: [packetId (Integer), reasonCode (ReasonCode), properties (MqttProperties)]
     */
    public static Object[] decodeAck(ByteBuffer buf, int remainingBytes, MqttVersion version) {
        int packetId = buf.getShort() & 0xFFFF;
        ReasonCode reasonCode = ReasonCode.SUCCESS;
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0 && remainingBytes > 2) {
            reasonCode = ReasonCode.fromValue(buf.get() & 0xFF);
            if (remainingBytes > 3) {
                properties = decodeProperties(buf);
            }
        }
        return new Object[]{packetId, reasonCode, properties};
    }

    /**
     * Decodes a SUBSCRIBE packet.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded SUBSCRIBE packet
     */
    public static SubscribePacket decodeSubscribe(ByteBuffer buf, int remainingBytes,
                                                  MqttVersion version) {
        int startPos = buf.position();
        int packetId = buf.getShort() & 0xFFFF;
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
        }

        List<TopicSubscription> subscriptions = new ArrayList<>();
        int endPos = startPos + remainingBytes;
        while (buf.position() < endPos) {
            String filter = decodeUtf8(buf);
            int options = buf.get() & 0xFF;
            QoS qos = QoS.fromValue(options & 0x03);
            boolean noLocal = (options & 0x04) != 0;
            boolean retainAsPublished = (options & 0x08) != 0;
            RetainHandling retainHandling = RetainHandling.fromValue((options >> 4) & 0x03);
            subscriptions.add(new TopicSubscription(filter, qos, noLocal, retainAsPublished, retainHandling));
        }
        return new SubscribePacket(packetId, subscriptions, properties);
    }

    /**
     * Decodes a SUBACK packet.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded SUBACK packet
     */
    public static SubAckPacket decodeSubAck(ByteBuffer buf, int remainingBytes,
                                            MqttVersion version) {
        int startPos = buf.position();
        int packetId = buf.getShort() & 0xFFFF;
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
        }
        List<ReasonCode> codes = new ArrayList<>();
        int endPos = startPos + remainingBytes;
        while (buf.position() < endPos) {
            codes.add(ReasonCode.fromValue(buf.get() & 0xFF));
        }
        return new SubAckPacket(packetId, codes, properties);
    }

    /**
     * Decodes an UNSUBSCRIBE packet.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded UNSUBSCRIBE packet
     */
    public static UnsubscribePacket decodeUnsubscribe(ByteBuffer buf, int remainingBytes,
                                                      MqttVersion version) {
        int startPos = buf.position();
        int packetId = buf.getShort() & 0xFFFF;
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
        }
        List<String> topics = new ArrayList<>();
        int endPos = startPos + remainingBytes;
        while (buf.position() < endPos) {
            topics.add(decodeUtf8(buf));
        }
        return new UnsubscribePacket(packetId, topics, properties);
    }

    /**
     * Decodes an UNSUBACK packet.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded UNSUBACK packet
     */
    public static UnsubAckPacket decodeUnsubAck(ByteBuffer buf, int remainingBytes,
                                                MqttVersion version) {
        int startPos = buf.position();
        int packetId = buf.getShort() & 0xFFFF;
        MqttProperties properties = new MqttProperties();
        List<ReasonCode> codes = new ArrayList<>();
        if (version == MqttVersion.V5_0) {
            properties = decodeProperties(buf);
            int endPos = startPos + remainingBytes;
            while (buf.position() < endPos) {
                codes.add(ReasonCode.fromValue(buf.get() & 0xFF));
            }
        }
        return new UnsubAckPacket(packetId, codes, properties);
    }

    /**
     * Decodes a DISCONNECT packet.
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @param version        the MQTT version
     * @return the decoded DISCONNECT packet
     */
    public static DisconnectPacket decodeDisconnect(ByteBuffer buf, int remainingBytes,
                                                    MqttVersion version) {
        ReasonCode reasonCode = ReasonCode.NORMAL_DISCONNECTION;
        MqttProperties properties = new MqttProperties();
        if (version == MqttVersion.V5_0 && remainingBytes > 0) {
            reasonCode = ReasonCode.fromValue(buf.get() & 0xFF);
            if (remainingBytes > 1) {
                properties = decodeProperties(buf);
            }
        }
        return new DisconnectPacket(reasonCode, properties);
    }

    /**
     * Decodes an AUTH packet (MQTT 5.0 only).
     *
     * @param buf            the buffer positioned at the variable header
     * @param remainingBytes total remaining bytes
     * @return the decoded AUTH packet
     */
    public static AuthPacket decodeAuth(ByteBuffer buf, int remainingBytes) {
        ReasonCode reasonCode = ReasonCode.SUCCESS;
        MqttProperties properties = new MqttProperties();
        if (remainingBytes > 0) {
            reasonCode = ReasonCode.fromValue(buf.get() & 0xFF);
            if (remainingBytes > 1) {
                properties = decodeProperties(buf);
            }
        }
        return new AuthPacket(reasonCode, properties);
    }

    // --- Helpers ---

    static String decodeUtf8(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] bytes = new byte[len];
        buf.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static byte[] decodeBinary(ByteBuffer buf) {
        int len = buf.getShort() & 0xFFFF;
        byte[] data = new byte[len];
        buf.get(data);
        return data;
    }

    static MqttProperties decodeProperties(ByteBuffer buf) {
        int propsLength = decodeVariableByteInteger(buf);
        if (propsLength == 0) {
            return new MqttProperties();
        }
        return MqttProperties.decode(buf, propsLength);
    }

    /**
     * Decodes a variable byte integer (MQTT remaining length encoding).
     *
     * @param buf the source buffer
     * @return the decoded integer value
     */
    static int decodeVariableByteInteger(ByteBuffer buf) {
        int multiplier = 1;
        int value = 0;
        int encodedByte;
        do {
            encodedByte = buf.get() & 0xFF;
            value += (encodedByte & 0x7F) * multiplier;
            multiplier *= 128;
            if (multiplier > 128 * 128 * 128 * 128) {
                throw new IllegalArgumentException("Malformed variable byte integer");
            }
        } while ((encodedByte & 0x80) != 0);
        return value;
    }
}
