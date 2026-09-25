package ssg.legoflow.messaging.mqtt.codec;

import ssg.legoflow.messaging.mqtt.protocol.*;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link MqttCodec} encode/decode round-trips.
 *
 * @since 0.1.0
 */
class MqttCodecTest {

    private final MqttCodec codec311 = new MqttCodec(MqttVersion.V3_1_1);
    private final MqttCodec codec50 = new MqttCodec(MqttVersion.V5_0);

    @Test
    void testConnectRoundTrip() {
        // Given: a CONNECT packet
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "test-client", true, 60,
                null, null, null, new MqttProperties());

        // When: encode then decode
        ByteBuffer encoded = codec311.encode(connect);
        var decoded = (ConnectPacket) codec311.decode(encoded);

        // Then: values match
        assertThat(decoded.clientId()).isEqualTo("test-client");
        assertThat(decoded.cleanSession()).isTrue();
        assertThat(decoded.keepAlive()).isEqualTo(60);
    }

    @Test
    void testConnectWithCredentialsRoundTrip() {
        // Given: CONNECT with username and password
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "auth-client", true, 30,
                "user", "pass", null, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(connect);
        var decoded = (ConnectPacket) codec311.decode(encoded);

        // Then: credentials preserved
        assertThat(decoded.username()).isEqualTo("user");
        assertThat(decoded.password()).isEqualTo("pass");
    }

    @Test
    void testConnectWithWillRoundTrip() {
        // Given: CONNECT with will message
        var will = new WillMessage("will/topic", "goodbye".getBytes(), QoS.AT_LEAST_ONCE, true);
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "will-client", true, 60,
                null, null, will, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(connect);
        var decoded = (ConnectPacket) codec311.decode(encoded);

        // Then: will preserved
        assertThat(decoded.will()).isNotNull();
        assertThat(decoded.will().topic()).isEqualTo("will/topic");
        assertThat(decoded.will().qos()).isEqualTo(QoS.AT_LEAST_ONCE);
        assertThat(decoded.will().retain()).isTrue();
    }

    @Test
    void testConnAckRoundTrip() {
        // Given: a CONNACK packet
        var connAck = new ConnAckPacket(true, ConnectReturnCode.ACCEPTED, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(connAck);
        var decoded = (ConnAckPacket) codec311.decode(encoded);

        // Then: values match
        assertThat(decoded.sessionPresent()).isTrue();
        assertThat(decoded.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);
    }

    @Test
    void testPublishQoS0RoundTrip() {
        // Given: QoS 0 PUBLISH
        var publish = new PublishPacket("test/topic", "hello".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(publish);
        var decoded = (PublishPacket) codec311.decode(encoded);

        // Then: values match
        assertThat(decoded.topic()).isEqualTo("test/topic");
        assertThat(new String(decoded.payload())).isEqualTo("hello");
        assertThat(decoded.qos()).isEqualTo(QoS.AT_MOST_ONCE);
    }

    @Test
    void testPublishQoS1RoundTrip() {
        // Given: QoS 1 PUBLISH with packet ID
        var publish = new PublishPacket("test/qos1", "data".getBytes(),
                QoS.AT_LEAST_ONCE, false, false, 42, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(publish);
        var decoded = (PublishPacket) codec311.decode(encoded);

        // Then: packet ID preserved
        assertThat(decoded.packetId()).isEqualTo(42);
        assertThat(decoded.qos()).isEqualTo(QoS.AT_LEAST_ONCE);
    }

    @Test
    void testPublishWithRetainAndDup() {
        // Given: PUBLISH with retain and dup flags
        var publish = new PublishPacket("flags/test", "msg".getBytes(),
                QoS.EXACTLY_ONCE, true, true, 100, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(publish);
        var decoded = (PublishPacket) codec311.decode(encoded);

        // Then: flags preserved
        assertThat(decoded.retain()).isTrue();
        assertThat(decoded.dup()).isTrue();
    }

    @Test
    void testPubAckRoundTrip() {
        // Given: PUBACK
        var pubAck = new PubAckPacket(7, ReasonCode.SUCCESS, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(pubAck);
        var decoded = (PubAckPacket) codec311.decode(encoded);

        // Then: packet ID matches
        assertThat(decoded.packetId()).isEqualTo(7);
    }

    @Test
    void testSubscribeRoundTrip() {
        // Given: SUBSCRIBE with two topics
        var sub = new SubscribePacket(10, List.of(
                new TopicSubscription("a/b", QoS.AT_LEAST_ONCE),
                new TopicSubscription("c/#", QoS.EXACTLY_ONCE)
        ), new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(sub);
        var decoded = (SubscribePacket) codec311.decode(encoded);

        // Then: subscriptions preserved
        assertThat(decoded.packetId()).isEqualTo(10);
        assertThat(decoded.subscriptions()).hasSize(2);
        assertThat(decoded.subscriptions().get(0).topicFilter()).isEqualTo("a/b");
        assertThat(decoded.subscriptions().get(1).qos()).isEqualTo(QoS.EXACTLY_ONCE);
    }

    @Test
    void testSubAckRoundTrip() {
        // Given: SUBACK with reason codes
        var subAck = new SubAckPacket(10, List.of(
                ReasonCode.GRANTED_QOS_1, ReasonCode.GRANTED_QOS_2
        ), new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(subAck);
        var decoded = (SubAckPacket) codec311.decode(encoded);

        // Then: codes match
        assertThat(decoded.packetId()).isEqualTo(10);
        assertThat(decoded.reasonCodes()).hasSize(2);
    }

    @Test
    void testUnsubscribeRoundTrip() {
        // Given: UNSUBSCRIBE
        var unsub = new UnsubscribePacket(15, List.of("a/b", "c/d"), new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(unsub);
        var decoded = (UnsubscribePacket) codec311.decode(encoded);

        // Then: topics match
        assertThat(decoded.packetId()).isEqualTo(15);
        assertThat(decoded.topics()).containsExactly("a/b", "c/d");
    }

    @Test
    void testPingReqRespRoundTrip() {
        // Given: PINGREQ and PINGRESP

        // When: round-trip
        ByteBuffer pingReqBuf = codec311.encode(new PingReqPacket());
        var pingReq = codec311.decode(pingReqBuf);

        ByteBuffer pingRespBuf = codec311.encode(new PingRespPacket());
        var pingResp = codec311.decode(pingRespBuf);

        // Then: correct types
        assertThat(pingReq).isInstanceOf(PingReqPacket.class);
        assertThat(pingResp).isInstanceOf(PingRespPacket.class);
    }

    @Test
    void testDisconnectRoundTrip() {
        // Given: DISCONNECT
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties());

        // When: round-trip
        ByteBuffer encoded = codec311.encode(disconnect);
        var decoded = (DisconnectPacket) codec311.decode(encoded);

        // Then: decoded without error (v3.1.1 has empty disconnect)
        assertThat(decoded).isNotNull();
    }

    @Test
    void testRemainingLengthEncoding() {
        // Given: a large payload requiring multi-byte remaining length
        byte[] largePayload = new byte[200];
        var publish = new PublishPacket("big", largePayload,
                QoS.AT_MOST_ONCE, false, false, 0, new MqttProperties());

        // When: encode and decode
        ByteBuffer encoded = codec311.encode(publish);
        var decoded = (PublishPacket) codec311.decode(encoded);

        // Then: payload size matches
        assertThat(decoded.payload()).hasSize(200);
    }

    @Test
    void testMqtt50ConnectWithProperties() {
        // Given: MQTT 5.0 CONNECT with properties
        var props = new MqttProperties().setSessionExpiryInterval(300L);
        var connect = new ConnectPacket(MqttVersion.V5_0, "v5-client", true, 60,
                null, null, null, props);

        // When: round-trip with v5.0 codec
        ByteBuffer encoded = codec50.encode(connect);
        var decoded = (ConnectPacket) codec50.decode(encoded);

        // Then: properties preserved
        assertThat(decoded.clientId()).isEqualTo("v5-client");
        assertThat(decoded.properties().getSessionExpiryInterval()).hasValue(300L);
    }
}
