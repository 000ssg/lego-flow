package ssg.legoflow.messaging.nats.jetstream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link AckPolicy}.
 */
class AckPolicyTest {

    @Test
    void testValues() {
        assertThat(AckPolicy.NONE.value()).isEqualTo("none");
        assertThat(AckPolicy.ALL.value()).isEqualTo("all");
        assertThat(AckPolicy.EXPLICIT.value()).isEqualTo("explicit");
    }

    @Test
    void testFromValue() {
        assertThat(AckPolicy.fromValue("none")).isEqualTo(AckPolicy.NONE);
        assertThat(AckPolicy.fromValue("all")).isEqualTo(AckPolicy.ALL);
        assertThat(AckPolicy.fromValue("explicit")).isEqualTo(AckPolicy.EXPLICIT);
    }

    @Test
    void testFromValueUnknown() {
        assertThatThrownBy(() -> AckPolicy.fromValue("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
