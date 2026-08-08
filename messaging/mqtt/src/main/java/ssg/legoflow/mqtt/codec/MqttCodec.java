package ssg.legoflow.mqtt.codec;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.mqtt.protocol.*;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * MQTT codec that encodes {@link MqttPacket} to {@link ByteBuffer} and vice versa.
 *
 * <p>Extends {@link AbstractDataFilter} to participate in the Lego Flow processing pipeline.
 * Handles the MQTT fixed header (packet type + flags + remaining length) and delegates
 * variable header / payload encoding/decoding to {@link MqttEncoder} and {@link MqttDecoder}.
 *
 * @since 0.1.0
 */
public class MqttCodec extends AbstractDataFilter<ByteBuffer> {

    private final MqttVersion version;
    private ByteBuffer accumulator;

    /**
     * Creates a new MQTT codec for the given protocol version.
     *
     * @param version the MQTT version to use for encoding/decoding
     */
    public MqttCodec(MqttVersion version) {
        super(ByteBuffer.class);
        this.version = version;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ByteBuffer[] doFilter(Context ctx, ByteBuffer... data) {
        // Pass through — codec is used via encode/decode methods
        return data;
    }

    /**
     * Encodes an MQTT packet into a {@link ByteBuffer} with fixed header.
     *
     * @param packet the packet to encode
     * @return the encoded buffer ready for transmission
     */
    public ByteBuffer encode(MqttPacket packet) {
        return switch (packet) {
            case ConnectPacket p -> encodeWithHeader(MqttPacketType.CONNECT, 0,
                    MqttEncoder.encodeConnect(p));
            case ConnAckPacket p -> encodeWithHeader(MqttPacketType.CONNACK, 0,
                    MqttEncoder.encodeConnAck(p, version));
            case PublishPacket p -> {
                int flags = 0;
                if (p.dup()) flags |= 0x08;
                flags |= (p.qos().value() << 1);
                if (p.retain()) flags |= 0x01;
                yield encodeWithHeader(MqttPacketType.PUBLISH, flags,
                        MqttEncoder.encodePublish(p, version));
            }
            case PubAckPacket p -> encodeWithHeader(MqttPacketType.PUBACK, 0,
                    MqttEncoder.encodeAck(p.packetId(), p.reasonCode(), p.properties(), version));
            case PubRecPacket p -> encodeWithHeader(MqttPacketType.PUBREC, 0,
                    MqttEncoder.encodeAck(p.packetId(), p.reasonCode(), p.properties(), version));
            case PubRelPacket p -> encodeWithHeader(MqttPacketType.PUBREL, 0x02,
                    MqttEncoder.encodeAck(p.packetId(), p.reasonCode(), p.properties(), version));
            case PubCompPacket p -> encodeWithHeader(MqttPacketType.PUBCOMP, 0,
                    MqttEncoder.encodeAck(p.packetId(), p.reasonCode(), p.properties(), version));
            case SubscribePacket p -> encodeWithHeader(MqttPacketType.SUBSCRIBE, 0x02,
                    MqttEncoder.encodeSubscribe(p, version));
            case SubAckPacket p -> encodeWithHeader(MqttPacketType.SUBACK, 0,
                    MqttEncoder.encodeSubAck(p, version));
            case UnsubscribePacket p -> encodeWithHeader(MqttPacketType.UNSUBSCRIBE, 0x02,
                    MqttEncoder.encodeUnsubscribe(p, version));
            case UnsubAckPacket p -> encodeWithHeader(MqttPacketType.UNSUBACK, 0,
                    MqttEncoder.encodeUnsubAck(p, version));
            case PingReqPacket p -> encodeWithHeader(MqttPacketType.PINGREQ, 0, ByteBuffer.allocate(0));
            case PingRespPacket p -> encodeWithHeader(MqttPacketType.PINGRESP, 0, ByteBuffer.allocate(0));
            case DisconnectPacket p -> encodeWithHeader(MqttPacketType.DISCONNECT, 0,
                    MqttEncoder.encodeDisconnect(p, version));
            case AuthPacket p -> encodeWithHeader(MqttPacketType.AUTH, 0,
                    MqttEncoder.encodeAuth(p));
        };
    }

    /**
     * Decodes an MQTT packet from a {@link ByteBuffer}.
     *
     * @param buf the buffer containing a complete MQTT packet
     * @return the decoded packet
     * @throws IllegalArgumentException if the packet is malformed
     */
    public MqttPacket decode(ByteBuffer buf) {
        int firstByte = buf.get() & 0xFF;
        int typeValue = firstByte >> 4;
        int flags = firstByte & 0x0F;
        MqttPacketType packetType = MqttPacketType.fromValue(typeValue);
        int remainingLength = MqttDecoder.decodeVariableByteInteger(buf);

        return switch (packetType) {
            case CONNECT -> MqttDecoder.decodeConnect(buf);
            case CONNACK -> MqttDecoder.decodeConnAck(buf, version);
            case PUBLISH -> MqttDecoder.decodePublish(buf, flags, remainingLength, version);
            case PUBACK -> {
                Object[] ack = MqttDecoder.decodeAck(buf, remainingLength, version);
                yield new PubAckPacket((int) ack[0], (ReasonCode) ack[1], (MqttProperties) ack[2]);
            }
            case PUBREC -> {
                Object[] ack = MqttDecoder.decodeAck(buf, remainingLength, version);
                yield new PubRecPacket((int) ack[0], (ReasonCode) ack[1], (MqttProperties) ack[2]);
            }
            case PUBREL -> {
                Object[] ack = MqttDecoder.decodeAck(buf, remainingLength, version);
                yield new PubRelPacket((int) ack[0], (ReasonCode) ack[1], (MqttProperties) ack[2]);
            }
            case PUBCOMP -> {
                Object[] ack = MqttDecoder.decodeAck(buf, remainingLength, version);
                yield new PubCompPacket((int) ack[0], (ReasonCode) ack[1], (MqttProperties) ack[2]);
            }
            case SUBSCRIBE -> MqttDecoder.decodeSubscribe(buf, remainingLength, version);
            case SUBACK -> MqttDecoder.decodeSubAck(buf, remainingLength, version);
            case UNSUBSCRIBE -> MqttDecoder.decodeUnsubscribe(buf, remainingLength, version);
            case UNSUBACK -> MqttDecoder.decodeUnsubAck(buf, remainingLength, version);
            case PINGREQ -> new PingReqPacket();
            case PINGRESP -> new PingRespPacket();
            case DISCONNECT -> MqttDecoder.decodeDisconnect(buf, remainingLength, version);
            case AUTH -> MqttDecoder.decodeAuth(buf, remainingLength);
        };
    }

    /**
     * Decodes all complete MQTT packets from the given buffer.
     *
     * <p>This is the stream-oriented entry point. It combines any previously
     * buffered partial data with the new input, decodes as many complete packets
     * as possible, and saves any remaining partial data in an internal accumulator
     * for the next call. Partial data across reads is normal, not an error.
     *
     * @param buf the buffer that may contain multiple (or partial) packets
     * @return a list of decoded packets
     */
    public List<MqttPacket> decodeAll(ByteBuffer buf) {
        var combined = combineWithAccumulator(buf);
        List<MqttPacket> packets = new ArrayList<>();

        while (combined.hasRemaining()) {
            combined.mark();
            if (combined.remaining() < 2) {
                combined.reset();
                break;
            }
            try {
                packets.add(decode(combined));
            } catch (BufferUnderflowException e) {
                // Partial packet — not enough data yet, save remainder
                combined.reset();
                break;
            }
        }

        // Save any remaining bytes to accumulator for next read
        if (combined.hasRemaining()) {
            accumulator = ByteBuffer.allocate(combined.remaining());
            accumulator.put(combined);
            accumulator.flip();
        } else {
            accumulator = null;
        }

        return packets;
    }

    /**
     * Returns whether this codec has buffered partial data from a previous
     * {@link #decodeAll(ByteBuffer)} call.
     *
     * @return {@code true} if partial data is buffered
     */
    public boolean hasBufferedData() {
        return accumulator != null && accumulator.hasRemaining();
    }

    /**
     * Combines the internal accumulator (if any) with new input data into
     * a single contiguous buffer.
     */
    private ByteBuffer combineWithAccumulator(ByteBuffer... data) {
        int totalSize = (accumulator != null ? accumulator.remaining() : 0);
        for (var buf : data) {
            totalSize += buf.remaining();
        }
        var combined = ByteBuffer.allocate(totalSize);
        if (accumulator != null) {
            combined.put(accumulator.duplicate());
            accumulator = null;
        }
        for (var buf : data) {
            combined.put(buf.duplicate());
        }
        combined.flip();
        return combined;
    }

    /**
     * Returns the MQTT version this codec operates with.
     *
     * @return the MQTT version
     */
    public MqttVersion getVersion() {
        return version;
    }

    // --- Private helpers ---

    private ByteBuffer encodeWithHeader(MqttPacketType type, int flags, ByteBuffer payload) {
        int remainingLength = payload.remaining();
        int headerSize = 1 + variableByteIntegerSize(remainingLength);
        var result = ByteBuffer.allocate(headerSize + remainingLength);

        result.put((byte) ((type.value() << 4) | (flags & 0x0F)));
        MqttEncoder.encodeVariableByteInteger(result, remainingLength);
        result.put(payload);
        result.flip();
        return result;
    }

    private int variableByteIntegerSize(int value) {
        int size = 0;
        do {
            value /= 128;
            size++;
        } while (value > 0);
        return size;
    }
}
