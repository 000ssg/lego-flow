package ssg.legoflow.mqtt.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MqttProperties} encode/decode.
 *
 * @since 1.0.0
 */
class MqttPropertiesTest {

    @Test
    void testEmptyProperties() {
        // Given: new empty properties
        var props = new MqttProperties();

        // When/Then: isEmpty returns true
        assertThat(props.isEmpty()).isTrue();
    }

    @Test
    void testSetAndGetPayloadFormatIndicator() {
        // Given: properties with payload format indicator
        var props = new MqttProperties().setPayloadFormatIndicator(1);

        // When/Then: get returns the value
        assertThat(props.getPayloadFormatIndicator()).hasValue(1);
    }

    @Test
    void testSetAndGetSessionExpiryInterval() {
        // Given: properties with session expiry
        var props = new MqttProperties().setSessionExpiryInterval(3600L);

        // When/Then: get returns the value
        assertThat(props.getSessionExpiryInterval()).hasValue(3600L);
    }

    @Test
    void testSetAndGetContentType() {
        // Given: properties with content type
        var props = new MqttProperties().setContentType("application/json");

        // When/Then: get returns the value
        assertThat(props.getContentType()).hasValue("application/json");
    }

    @Test
    void testUserProperties() {
        // Given: properties with user properties
        var props = new MqttProperties()
                .addUserProperty("key1", "value1")
                .addUserProperty("key2", "value2");

        // When/Then: user properties are retrievable
        assertThat(props.getUserProperties()).hasSize(2);
        assertThat(props.getUserProperties().get(0).key()).isEqualTo("key1");
        assertThat(props.getUserProperties().get(1).value()).isEqualTo("value2");
        assertThat(props.isEmpty()).isFalse();
    }

    @Test
    void testEncodeDecodeRoundTrip() {
        // Given: properties with multiple values
        var original = new MqttProperties()
                .setPayloadFormatIndicator(1)
                .setContentType("text/plain")
                .setSessionExpiryInterval(7200L)
                .addUserProperty("app", "test");

        // When: encode then decode
        ByteBuffer encoded = original.encode();
        var decoded = MqttProperties.decode(encoded, encoded.remaining());

        // Then: values match
        assertThat(decoded.getPayloadFormatIndicator()).hasValue(1);
        assertThat(decoded.getContentType()).hasValue("text/plain");
        assertThat(decoded.getSessionExpiryInterval()).hasValue(7200L);
        assertThat(decoded.getUserProperties()).hasSize(1);
        assertThat(decoded.getUserProperties().get(0).key()).isEqualTo("app");
    }

    @Test
    void testBooleanProperties() {
        // Given: properties with boolean flags
        var props = new MqttProperties()
                .setRetainAvailable(true)
                .setWildcardSubscriptionAvailable(false);

        // When/Then: boolean getters work
        assertThat(props.getRetainAvailable()).hasValue(true);
        assertThat(props.getWildcardSubscriptionAvailable()).hasValue(false);
    }

    @Test
    void testCorrelationDataRoundTrip() {
        // Given: properties with binary data
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        var props = new MqttProperties().setCorrelationData(data);

        // When: encode and decode
        ByteBuffer encoded = props.encode();
        var decoded = MqttProperties.decode(encoded, encoded.remaining());

        // Then: data matches
        assertThat(decoded.getCorrelationData()).isPresent();
        assertThat(decoded.getCorrelationData().get()).containsExactly(0x01, 0x02, 0x03, 0x04);
    }
}
