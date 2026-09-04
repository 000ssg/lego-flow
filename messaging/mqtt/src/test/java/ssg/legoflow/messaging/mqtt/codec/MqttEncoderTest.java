package ssg.legoflow.messaging.mqtt.codec;

import ssg.legoflow.messaging.mqtt.protocol.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttEncoder}.
 *
 * @since 0.1.0
 */
class MqttEncoderTest {

    @Test
    void testEncodeConnectMinimal() {
        // Given: minimal CONNECT
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "c1", true, 60,
                null, null, null, new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodeConnect(connect);

        // Then: buffer has content
        assertThat(buf.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeConnectWithAuth() {
        // Given: CONNECT with credentials
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "c2", true, 60,
                "admin", "secret", null, new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodeConnect(connect);

        // Then: buffer has content (larger than minimal)
        assertThat(buf.remaining()).isGreaterThan(20);
    }

    @Test
    void testEncodeConnAck() {
        // Given: CONNACK
        var connAck = new ConnAckPacket(false, ConnectReturnCode.ACCEPTED, new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodeConnAck(connAck, MqttVersion.V3_1_1);

        // Then: 2 bytes for v3.1.1 (flags + return code)
        assertThat(buf.remaining()).isEqualTo(2);
    }

    @Test
    void testEncodePublishQoS0() {
        // Given: QoS 0 PUBLISH (no packet ID)
        var publish = new PublishPacket("t/1", "data".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodePublish(publish, MqttVersion.V3_1_1);

        // Then: buffer contains topic + payload, no packet ID
        assertThat(buf.remaining()).isEqualTo(2 + 3 + 4); // length prefix + topic + payload
    }

    @Test
    void testEncodePublishQoS1IncludesPacketId() {
        // Given: QoS 1 PUBLISH
        var publish = new PublishPacket("t/1", "data".getBytes(),
                QoS.AT_LEAST_ONCE, false, false, 5, new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodePublish(publish, MqttVersion.V3_1_1);

        // Then: buffer includes packet ID (2 extra bytes)
        assertThat(buf.remaining()).isEqualTo(2 + 3 + 2 + 4);
    }

    @Test
    void testEncodeAck() {
        // Given: PUBACK for v3.1.1
        ByteBuffer buf = MqttEncoder.encodeAck(42, ReasonCode.SUCCESS,
                new MqttProperties(), MqttVersion.V3_1_1);

        // Then: just 2 bytes (packet ID)
        assertThat(buf.remaining()).isEqualTo(2);
    }

    @Test
    void testEncodeSubscribe() {
        // Given: SUBSCRIBE with one topic
        var sub = new SubscribePacket(1, List.of(
                new TopicSubscription("a/b", QoS.AT_LEAST_ONCE)
        ), new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodeSubscribe(sub, MqttVersion.V3_1_1);

        // Then: has content
        assertThat(buf.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeUnsubscribe() {
        // Given: UNSUBSCRIBE with two topics
        var unsub = new UnsubscribePacket(2, List.of("x", "y"), new MqttProperties());

        // When: encode
        ByteBuffer buf = MqttEncoder.encodeUnsubscribe(unsub, MqttVersion.V3_1_1);

        // Then: has content
        assertThat(buf.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeVariableByteIntegerSmall() {
        // Given: small value
        ByteBuffer buf = ByteBuffer.allocate(4);
        MqttEncoder.encodeVariableByteInteger(buf, 127);
        buf.flip();

        // Then: single byte
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get() & 0xFF).isEqualTo(127);
    }

    @Test
    void testEncodeVariableByteIntegerMultiByte() {
        // Given: value requiring 2 bytes
        ByteBuffer buf = ByteBuffer.allocate(4);
        MqttEncoder.encodeVariableByteInteger(buf, 128);
        buf.flip();

        // Then: two bytes with continuation
        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(buf.get() & 0xFF).isEqualTo(0x80);
        assertThat(buf.get() & 0xFF).isEqualTo(0x01);
    }
}
