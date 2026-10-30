package ssg.legoflow.messaging.mqtt.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

class MqttPropertiesTest {

    @Test void emptyPropertiesIsEmpty() {
        var props = new MqttProperties();
        assertThat(props.isEmpty()).isTrue();
    }

    @Test void nonEmptyPropertiesIsNotEmpty() {
        var props = new MqttProperties()
            .setPayloadFormatIndicator(1);
        assertThat(props.isEmpty()).isFalse();
    }

    @Test void userPropertyMakesNonEmpty() {
        var props = new MqttProperties()
            .addUserProperty("key", "value");
        assertThat(props.isEmpty()).isFalse();
    }

    // --- Setter + getter round-trips ---

    @Test void payloadFormatIndicatorRoundTrip() {
        var props = new MqttProperties()
            .setPayloadFormatIndicator(1);
        assertThat(props.getPayloadFormatIndicator()).contains(1);
    }

    @Test void messageExpiryIntervalRoundTrip() {
        var props = new MqttProperties()
            .setMessageExpiryInterval(3600L);
        assertThat(props.getMessageExpiryInterval()).contains(3600L);
    }

    @Test void contentTypeRoundTrip() {
        var props = new MqttProperties()
            .setContentType("application/json");
        assertThat(props.getContentType()).contains("application/json");
    }

    @Test void responseTopicRoundTrip() {
        var props = new MqttProperties()
            .setResponseTopic("$response/topic");
        assertThat(props.getResponseTopic()).contains("$response/topic");
    }

    @Test void correlationDataRoundTrip() {
        var data = new byte[]{1, 2, 3};
        var props = new MqttProperties()
            .setCorrelationData(data);
        var result = props.getCorrelationData();
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(1, 2, 3);
    }

    @Test void sessionExpiryIntervalRoundTrip() {
        var props = new MqttProperties()
            .setSessionExpiryInterval(7200L);
        assertThat(props.getSessionExpiryInterval()).contains(7200L);
    }

    @Test void assignedClientIdentifierRoundTrip() {
        var props = new MqttProperties()
            .setAssignedClientIdentifier("srv-assigned-id");
        assertThat(props.getAssignedClientIdentifier()).contains("srv-assigned-id");
    }

    @Test void serverKeepAliveRoundTrip() {
        var props = new MqttProperties()
            .setServerKeepAlive(120);
        assertThat(props.getServerKeepAlive()).contains(120);
    }

    @Test void authenticationMethodRoundTrip() {
        var props = new MqttProperties()
            .setAuthenticationMethod("X-Accepted-OAuth");
        assertThat(props.getAuthenticationMethod()).contains("X-Accepted-OAuth");
    }

    @Test void authenticationDataRoundTrip() {
        var data = new byte[]{10, 20};
        var props = new MqttProperties()
            .setAuthenticationData(data);
        var result = props.getAuthenticationData();
        assertThat(result).isPresent();
        assertThat(result.get()).containsExactly(10, 20);
    }

    @Test void receiveMaximumRoundTrip() {
        var props = new MqttProperties()
            .setReceiveMaximum(100);
        assertThat(props.getReceiveMaximum()).contains(100);
    }

    @Test void topicAliasRoundTrip() {
        var props = new MqttProperties()
            .setTopicAlias(5);
        assertThat(props.getTopicAlias()).contains(5);
    }

    @Test void maximumQosRoundTrip() {
        var props = new MqttProperties()
            .setMaximumQos(1);
        assertThat(props.getMaximumQos()).contains(1);
    }

    @Test void retainAvailableRoundTrip() {
        var props = new MqttProperties()
            .setRetainAvailable(true);
        assertThat(props.getRetainAvailable()).contains(true);

        var props2 = new MqttProperties()
            .setRetainAvailable(false);
        assertThat(props2.getRetainAvailable()).contains(false);
    }

    @Test void maximumPacketSizeRoundTrip() {
        var props = new MqttProperties()
            .setMaximumPacketSize(65536L);
        assertThat(props.getMaximumPacketSize()).contains(65536L);
    }

    @Test void wildcardSubscriptionAvailableRoundTrip() {
        var props = new MqttProperties()
            .setWildcardSubscriptionAvailable(true);
        assertThat(props.getWildcardSubscriptionAvailable()).contains(true);
    }

    @Test void subscriptionIdentifierAvailableRoundTrip() {
        var props = new MqttProperties()
            .setSubscriptionIdentifierAvailable(true);
        assertThat(props.getSubscriptionIdentifierAvailable()).contains(true);
    }

    @Test void sharedSubscriptionAvailableRoundTrip() {
        var props = new MqttProperties()
            .setSharedSubscriptionAvailable(true);
        assertThat(props.getSharedSubscriptionAvailable()).contains(true);
    }

    @Test void userPropertiesRoundTrip() {
        var props = new MqttProperties()
            .addUserProperty("tenant", "acme")
            .addUserProperty("env", "prod");
        var list = props.getUserProperties();
        assertThat(list).hasSize(2);
        assertThat(list.get(0).key()).isEqualTo("tenant");
        assertThat(list.get(0).value()).isEqualTo("acme");
        assertThat(list.get(1).key()).isEqualTo("env");
        assertThat(list.get(1).value()).isEqualTo("prod");
    }

    @Test void userPropertiesUnmodifiable() {
        var props = new MqttProperties();
        assertThatThrownBy(() -> props.getUserProperties().add(
            new MqttProperties.UserProperty("x", "y")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void gettersReturnEmptyWhenNotSet() {
        var props = new MqttProperties();
        assertThat(props.getPayloadFormatIndicator()).isEmpty();
        assertThat(props.getMessageExpiryInterval()).isEmpty();
        assertThat(props.getContentType()).isEmpty();
        assertThat(props.getResponseTopic()).isEmpty();
        assertThat(props.getCorrelationData()).isEmpty();
        assertThat(props.getSessionExpiryInterval()).isEmpty();
        assertThat(props.getAssignedClientIdentifier()).isEmpty();
        assertThat(props.getServerKeepAlive()).isEmpty();
        assertThat(props.getAuthenticationMethod()).isEmpty();
        assertThat(props.getAuthenticationData()).isEmpty();
        assertThat(props.getReceiveMaximum()).isEmpty();
        assertThat(props.getTopicAlias()).isEmpty();
        assertThat(props.getMaximumQos()).isEmpty();
        assertThat(props.getRetainAvailable()).isEmpty();
        assertThat(props.getMaximumPacketSize()).isEmpty();
        assertThat(props.getWildcardSubscriptionAvailable()).isEmpty();
        assertThat(props.getSubscriptionIdentifierAvailable()).isEmpty();
        assertThat(props.getSharedSubscriptionAvailable()).isEmpty();
        assertThat(props.getUserProperties()).isEmpty();
    }

    // --- Encode/decode round-trip tests ---

    @Test void encodeDecodeEmptyProperties() {
        var props = new MqttProperties();
        var encoded = props.encode();
        encoded.flip(); // flip back for decode
        var decoded = MqttProperties.decode(encoded, encoded.remaining());
        assertThat(decoded.isEmpty()).isTrue();
    }

    @Test void encodeDecodeSimpleIntProperties() {
        var props = new MqttProperties()
            .setPayloadFormatIndicator(1)
            .setMaximumQos(1)
            .setRetainAvailable(true)
            .setServerKeepAlive(60)
            .setTopicAlias(3)
            .setReceiveMaximum(200);

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        assertThat(decoded.getPayloadFormatIndicator()).contains(1);
        assertThat(decoded.getMaximumQos()).contains(1);
        assertThat(decoded.getRetainAvailable()).contains(true);
        assertThat(decoded.getServerKeepAlive()).contains(60);
        assertThat(decoded.getTopicAlias()).contains(3);
        assertThat(decoded.getReceiveMaximum()).contains(200);
    }

    @Test void encodeDecodeLongProperties() {
        var props = new MqttProperties()
            .setMessageExpiryInterval(86400L)
            .setSessionExpiryInterval(43200L)
            .setMaximumPacketSize(131072L);

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        assertThat(decoded.getMessageExpiryInterval()).contains(86400L);
        assertThat(decoded.getSessionExpiryInterval()).contains(43200L);
        assertThat(decoded.getMaximumPacketSize()).contains(131072L);
    }

    @Test void encodeDecodeStringProperties() {
        var props = new MqttProperties()
            .setContentType("text/plain")
            .setResponseTopic("resp")
            .setAssignedClientIdentifier("cli-123")
            .setAuthenticationMethod("digest");

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        assertThat(decoded.getContentType()).contains("text/plain");
        assertThat(decoded.getResponseTopic()).contains("resp");
        assertThat(decoded.getAssignedClientIdentifier()).contains("cli-123");
        assertThat(decoded.getAuthenticationMethod()).contains("digest");
    }

    @Test void encodeDecodeByteArrayProperties() {
        var props = new MqttProperties()
            .setCorrelationData(new byte[]{1, 2, 3})
            .setAuthenticationData(new byte[]{4, 5});

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        var corr = decoded.getCorrelationData();
        assertThat(corr).isPresent();
        assertThat(corr.get()).containsExactly(1, 2, 3);

        var auth = decoded.getAuthenticationData();
        assertThat(auth).isPresent();
        assertThat(auth.get()).containsExactly(4, 5);
    }

    @Test void encodeDecodeUserProperties() {
        var props = new MqttProperties()
            .addUserProperty("key1", "val1")
            .addUserProperty("key2", "val2");

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        var ups = decoded.getUserProperties();
        assertThat(ups).hasSize(2);
        assertThat(ups.get(0).key()).isEqualTo("key1");
        assertThat(ups.get(0).value()).isEqualTo("val1");
        assertThat(ups.get(1).key()).isEqualTo("key2");
        assertThat(ups.get(1).value()).isEqualTo("val2");
    }

    @Test void encodeDecodeAllProperties() {
        var props = new MqttProperties()
            .setPayloadFormatIndicator(1)
            .setMessageExpiryInterval(3600L)
            .setContentType("app/json")
            .setResponseTopic("reply")
            .setCorrelationData(new byte[]{42})
            .setSessionExpiryInterval(7200L)
            .setAssignedClientIdentifier("auto-id")
            .setServerKeepAlive(90)
            .setAuthenticationMethod("oauth")
            .setAuthenticationData(new byte[]{1, 1})
            .setReceiveMaximum(256)
            .setTopicAlias(10)
            .setMaximumQos(2)
            .setRetainAvailable(true)
            .setMaximumPacketSize(262144L)
            .setWildcardSubscriptionAvailable(true)
            .setSubscriptionIdentifierAvailable(true)
            .setSharedSubscriptionAvailable(true)
            .addUserProperty("x", "y");

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        // Verify all properties survived
        assertThat(decoded.getPayloadFormatIndicator()).contains(1);
        assertThat(decoded.getMessageExpiryInterval()).contains(3600L);
        assertThat(decoded.getContentType()).contains("app/json");
        assertThat(decoded.getResponseTopic()).contains("reply");
        assertThat(decoded.getCorrelationData()).isPresent();
        assertThat(decoded.getSessionExpiryInterval()).contains(7200L);
        assertThat(decoded.getAssignedClientIdentifier()).contains("auto-id");
        assertThat(decoded.getServerKeepAlive()).contains(90);
        assertThat(decoded.getAuthenticationMethod()).contains("oauth");
        assertThat(decoded.getAuthenticationData()).isPresent();
        assertThat(decoded.getReceiveMaximum()).contains(256);
        assertThat(decoded.getTopicAlias()).contains(10);
        assertThat(decoded.getMaximumQos()).contains(2);
        assertThat(decoded.getRetainAvailable()).contains(true);
        assertThat(decoded.getMaximumPacketSize()).contains(262144L);
        assertThat(decoded.getWildcardSubscriptionAvailable()).contains(true);
        assertThat(decoded.getSubscriptionIdentifierAvailable()).contains(true);
        assertThat(decoded.getSharedSubscriptionAvailable()).contains(true);
        assertThat(decoded.getUserProperties()).hasSize(1);
    }

    @Test void encodeDecodeWithUnicodeStrings() {
        var props = new MqttProperties()
            .setContentType("text/unicode")
            .setResponseTopic(" thème/日本語");

        var encoded = props.encode();
        var decoded = roundTrip(encoded);

        assertThat(decoded.getContentType()).contains("text/unicode");
        assertThat(decoded.getResponseTopic()).contains(" thème/日本語");
    }

    @Test void chainingReturnsSameInstance() {
        var props = new MqttProperties();
        assertThat(props.setPayloadFormatIndicator(1)).isSameAs(props);
        assertThat(props.setMessageExpiryInterval(100L)).isSameAs(props);
        assertThat(props.setContentType("x")).isSameAs(props);
        assertThat(props.setResponseTopic("x")).isSameAs(props);
        assertThat(props.setCorrelationData(new byte[0])).isSameAs(props);
        assertThat(props.setSessionExpiryInterval(100L)).isSameAs(props);
        assertThat(props.setAssignedClientIdentifier("x")).isSameAs(props);
        assertThat(props.setServerKeepAlive(10)).isSameAs(props);
        assertThat(props.setAuthenticationMethod("x")).isSameAs(props);
        assertThat(props.setAuthenticationData(new byte[0])).isSameAs(props);
        assertThat(props.setReceiveMaximum(10)).isSameAs(props);
        assertThat(props.setTopicAlias(1)).isSameAs(props);
        assertThat(props.setMaximumQos(1)).isSameAs(props);
        assertThat(props.setRetainAvailable(true)).isSameAs(props);
        assertThat(props.setMaximumPacketSize(1024L)).isSameAs(props);
        assertThat(props.setWildcardSubscriptionAvailable(true)).isSameAs(props);
        assertThat(props.setSubscriptionIdentifierAvailable(true)).isSameAs(props);
        assertThat(props.setSharedSubscriptionAvailable(true)).isSameAs(props);
        assertThat(props.addUserProperty("k", "v")).isSameAs(props);
    }

    @Test void userPropertyRecordAccessors() {
        var up = new MqttProperties.UserProperty("k", "v");
        assertThat(up.key()).isEqualTo("k");
        assertThat(up.value()).isEqualTo("v");
    }

    @Test void userPropertyConstantExists() {
        assertThat(MqttProperties.USER_PROPERTY).isEqualTo(0x26);
        assertThat(MqttProperties.PAYLOAD_FORMAT_INDICATOR).isEqualTo(0x01);
        assertThat(MqttProperties.MESSAGE_EXPIRY_INTERVAL).isEqualTo(0x02);
    }

    @Test void authPacketType() {
        var auth = new AuthPacket(ReasonCode.SUCCESS, new MqttProperties());
        assertThat(auth.type()).isEqualTo(MqttPacketType.AUTH);
        assertThat(auth.reasonCode()).isEqualTo(ReasonCode.SUCCESS);
        assertThat(auth.properties()).isNotNull();
    }

    @Test void pingReqPacketType() {
        var pkt = new PingReqPacket();
        assertThat(pkt.type()).isEqualTo(MqttPacketType.PINGREQ);
    }

    @Test void pingRespPacketType() {
        var pkt = new PingRespPacket();
        assertThat(pkt.type()).isEqualTo(MqttPacketType.PINGRESP);
    }

    @Test void publishPacketType() {
        var pkt = new PublishPacket("t", "payload".getBytes(), QoS.AT_LEAST_ONCE, false, false, 0, new MqttProperties());
        assertThat(pkt.type()).isEqualTo(MqttPacketType.PUBLISH);
    }

    @Test void connAckPacketType() {
        var pkt = new ConnAckPacket(true, ConnectReturnCode.ACCEPTED, new MqttProperties());
        assertThat(pkt.type()).isEqualTo(MqttPacketType.CONNACK);
    }

    @Test void pubAckPacketType() {
        var pkt = new PubAckPacket(0, ReasonCode.SUCCESS, new MqttProperties());
        assertThat(pkt.type()).isEqualTo(MqttPacketType.PUBACK);
    }

    @Test void subscribePacketType() {
        var sub = new TopicSubscription("t", QoS.AT_MOST_ONCE);
        var pkt = new SubscribePacket(0, List.of(sub), new MqttProperties());
        assertThat(pkt.type()).isEqualTo(MqttPacketType.SUBSCRIBE);
    }

    @Test void unsubscribePacketType() {
        var pkt = new UnsubscribePacket(0, List.of("t"), new MqttProperties());
        assertThat(pkt.type()).isEqualTo(MqttPacketType.UNSUBSCRIBE);
    }

    /** Helper: encode → decode round trip. */
    private MqttProperties roundTrip(ByteBuffer encoded) {
        // encode() already flips the buffer, so position=0, limit=length
        int len = encoded.remaining();
        return MqttProperties.decode(encoded, len);
    }
}
