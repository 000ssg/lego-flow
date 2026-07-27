package ssg.legoflow.mqtt.codec;

import ssg.legoflow.mqtt.protocol.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MqttDecoder}.
 *
 * @since 1.0.0
 */
class MqttDecoderTest {

    private final MqttCodec codec = new MqttCodec(MqttVersion.V3_1_1);
    private final MqttCodec codec50 = new MqttCodec(MqttVersion.V5_0);

    @Test
    void testDecodeConnectBasic() {
        // Given: encoded CONNECT
        var original = new ConnectPacket(MqttVersion.V3_1_1, "client-1", true, 60,
                null, null, null, new MqttProperties());
        ByteBuffer buf = codec.encode(original);

        // When: decode
        var decoded = (ConnectPacket) codec.decode(buf);

        // Then: client ID matches
        assertThat(decoded.clientId()).isEqualTo("client-1");
        assertThat(decoded.version()).isEqualTo(MqttVersion.V3_1_1);
    }

    @Test
    void testDecodeConnAck() {
        // Given: encoded CONNACK
        var original = new ConnAckPacket(false, ConnectReturnCode.BAD_CREDENTIALS,
                new MqttProperties());
        ByteBuffer buf = codec.encode(original);

        // When: decode
        var decoded = (ConnAckPacket) codec.decode(buf);

        // Then: return code matches
        assertThat(decoded.returnCode()).isEqualTo(ConnectReturnCode.BAD_CREDENTIALS);
        assertThat(decoded.sessionPresent()).isFalse();
    }

    @Test
    void testDecodePublishEmptyPayload() {
        // Given: PUBLISH with empty payload
        var original = new PublishPacket("empty", new byte[0],
                QoS.AT_MOST_ONCE, false, false, 0, new MqttProperties());
        ByteBuffer buf = codec.encode(original);

        // When: decode
        var decoded = (PublishPacket) codec.decode(buf);

        // Then: empty payload
        assertThat(decoded.payload()).isEmpty();
        assertThat(decoded.topic()).isEqualTo("empty");
    }

    @Test
    void testDecodeSubscribeMultipleTopics() {
        // Given: SUBSCRIBE with 3 topics
        var original = new SubscribePacket(20, List.of(
                new TopicSubscription("a", QoS.AT_MOST_ONCE),
                new TopicSubscription("b/+", QoS.AT_LEAST_ONCE),
                new TopicSubscription("c/#", QoS.EXACTLY_ONCE)
        ), new MqttProperties());
        ByteBuffer buf = codec.encode(original);

        // When: decode
        var decoded = (SubscribePacket) codec.decode(buf);

        // Then: all subscriptions present
        assertThat(decoded.subscriptions()).hasSize(3);
        assertThat(decoded.subscriptions().get(2).qos()).isEqualTo(QoS.EXACTLY_ONCE);
    }

    @Test
    void testDecodeUnsubscribe() {
        // Given: UNSUBSCRIBE
        var original = new UnsubscribePacket(5, List.of("topic1", "topic2"),
                new MqttProperties());
        ByteBuffer buf = codec.encode(original);

        // When: decode
        var decoded = (UnsubscribePacket) codec.decode(buf);

        // Then: topics match
        assertThat(decoded.topics()).containsExactly("topic1", "topic2");
    }

    @Test
    void testDecodePingReq() {
        // Given: PINGREQ
        ByteBuffer buf = codec.encode(new PingReqPacket());

        // When: decode
        var decoded = codec.decode(buf);

        // Then: correct type
        assertThat(decoded).isInstanceOf(PingReqPacket.class);
    }

    @Test
    void testDecodePingResp() {
        // Given: PINGRESP
        ByteBuffer buf = codec.encode(new PingRespPacket());

        // When: decode
        var decoded = codec.decode(buf);

        // Then: correct type
        assertThat(decoded).isInstanceOf(PingRespPacket.class);
    }

    @Test
    void testDecodeVariableByteIntegerZero() {
        // Given: buffer with single 0 byte
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{0x00});

        // When: decode
        int value = MqttDecoder.decodeVariableByteInteger(buf);

        // Then: zero
        assertThat(value).isEqualTo(0);
    }

    @Test
    void testDecodeVariableByteIntegerMax() {
        // Given: buffer encoding 16383 (max 2-byte)
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{(byte) 0xFF, 0x7F});

        // When: decode
        int value = MqttDecoder.decodeVariableByteInteger(buf);

        // Then: 16383
        assertThat(value).isEqualTo(16383);
    }

    @Test
    void testDecodeVariableByteIntegerMalformed() {
        // Given: more than 4 continuation bytes
        ByteBuffer buf = ByteBuffer.wrap(new byte[]{
                (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80});

        // When/Then: throws (IllegalArgumentException or BufferUnderflowException)
        assertThatThrownBy(() -> MqttDecoder.decodeVariableByteInteger(buf))
                .isInstanceOf(RuntimeException.class);
    }
}
