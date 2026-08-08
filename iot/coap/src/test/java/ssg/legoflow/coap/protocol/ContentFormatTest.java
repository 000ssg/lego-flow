package ssg.legoflow.coap.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link ContentFormat}.
 *
 * @since 0.1.0
 */
class ContentFormatTest {

    @Test
    void testTextPlainValue() {
        assertThat(ContentFormat.TEXT_PLAIN.value()).isZero();
    }

    @Test
    void testFromValueJson() {
        assertThat(ContentFormat.fromValue(50)).isEqualTo(ContentFormat.APPLICATION_JSON);
    }

    @Test
    void testFromValueCbor() {
        assertThat(ContentFormat.fromValue(60)).isEqualTo(ContentFormat.APPLICATION_CBOR);
    }

    @Test
    void testFromValueUnknownThrows() {
        assertThatThrownBy(() -> ContentFormat.fromValue(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }
}
