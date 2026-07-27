package ssg.legoflow.mqtt.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link MqttPacketType}.
 *
 * @since 1.0.0
 */
class MqttPacketTypeTest {

    @Test
    void testAllPacketTypesHaveCorrectValues() {
        // Given: all 15 MQTT packet types

        // When/Then: each has its expected numeric value
        assertThat(MqttPacketType.CONNECT.value()).isEqualTo(1);
        assertThat(MqttPacketType.CONNACK.value()).isEqualTo(2);
        assertThat(MqttPacketType.PUBLISH.value()).isEqualTo(3);
        assertThat(MqttPacketType.PUBACK.value()).isEqualTo(4);
        assertThat(MqttPacketType.AUTH.value()).isEqualTo(15);
    }

    @Test
    void testFromValueResolvesCorrectly() {
        // Given: valid packet type values

        // When/Then: fromValue resolves each
        assertThat(MqttPacketType.fromValue(1)).isEqualTo(MqttPacketType.CONNECT);
        assertThat(MqttPacketType.fromValue(3)).isEqualTo(MqttPacketType.PUBLISH);
        assertThat(MqttPacketType.fromValue(14)).isEqualTo(MqttPacketType.DISCONNECT);
    }

    @Test
    void testFromValueThrowsForInvalidValue() {
        // Given: an invalid packet type value

        // When/Then: throws IllegalArgumentException
        assertThatThrownBy(() -> MqttPacketType.fromValue(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttPacketType.fromValue(16))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEnumContainsAllFifteenTypes() {
        // Given/When: all enum values
        MqttPacketType[] types = MqttPacketType.values();

        // Then: exactly 15 types
        assertThat(types).hasSize(15);
    }

    @Test
    void testPacketTypeNames() {
        // Given/When/Then: verify select names
        assertThat(MqttPacketType.SUBSCRIBE.name()).isEqualTo("SUBSCRIBE");
        assertThat(MqttPacketType.PINGREQ.name()).isEqualTo("PINGREQ");
        assertThat(MqttPacketType.PINGRESP.name()).isEqualTo("PINGRESP");
    }
}
