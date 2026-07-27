package ssg.legoflow.mqtt.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link QoS}.
 *
 * @since 1.0.0
 */
class QoSTest {

    @Test
    void testQoSValues() {
        // Given/When/Then: each QoS has its expected numeric value
        assertThat(QoS.AT_MOST_ONCE.value()).isEqualTo(0);
        assertThat(QoS.AT_LEAST_ONCE.value()).isEqualTo(1);
        assertThat(QoS.EXACTLY_ONCE.value()).isEqualTo(2);
    }

    @Test
    void testFromValueResolvesCorrectly() {
        // Given/When/Then: fromValue resolves
        assertThat(QoS.fromValue(0)).isEqualTo(QoS.AT_MOST_ONCE);
        assertThat(QoS.fromValue(1)).isEqualTo(QoS.AT_LEAST_ONCE);
        assertThat(QoS.fromValue(2)).isEqualTo(QoS.EXACTLY_ONCE);
    }

    @Test
    void testFromValueThrowsForInvalidValue() {
        // Given/When/Then: invalid QoS throws
        assertThatThrownBy(() -> QoS.fromValue(3))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> QoS.fromValue(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testEnumContainsThreeLevels() {
        // Given/When/Then: exactly 3 QoS levels
        assertThat(QoS.values()).hasSize(3);
    }
}
