package ssg.legoflow.coap.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CoapOption}.
 *
 * @since 1.0.0
 */
class CoapOptionTest {

    @Test
    void testUriPathFactory() {
        var option = CoapOption.uriPath("sensors");

        assertThat(option.number()).isEqualTo(CoapOption.URI_PATH);
        assertThat(option.asString()).isEqualTo("sensors");
    }

    @Test
    void testContentFormatFactory() {
        var option = CoapOption.contentFormat(ContentFormat.APPLICATION_JSON.value());

        assertThat(option.number()).isEqualTo(CoapOption.CONTENT_FORMAT);
        assertThat(option.asInt()).isEqualTo(50);
    }

    @Test
    void testEtagFactory() {
        var etag = new byte[]{0x01, 0x02, 0x03};
        var option = CoapOption.etag(etag);

        assertThat(option.number()).isEqualTo(CoapOption.ETAG);
        assertThat(option.value()).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void testAsInt() {
        var option = new CoapOption(CoapOption.URI_PORT, new byte[]{0x1F, (byte) 0x90}); // 8080

        assertThat(option.asInt()).isEqualTo(8080);
    }

    @Test
    void testAsLong() {
        var option = CoapOption.maxAge(86400);

        assertThat(option.asLong()).isEqualTo(86400L);
    }

    @Test
    void testEncodeUintZero() {
        var option = CoapOption.contentFormat(0);

        // Zero encodes as empty byte array
        assertThat(option.value()).isEmpty();
        assertThat(option.asInt()).isZero();
    }

    @Test
    void testDefensiveCopy() {
        var value = new byte[]{0x01, 0x02};
        var option = new CoapOption(CoapOption.ETAG, value);

        // Modify original
        value[0] = (byte) 0xFF;

        // Option should be unchanged
        assertThat(option.value()[0]).isEqualTo((byte) 0x01);
    }

    @Test
    void testNegativeNumberThrows() {
        assertThatThrownBy(() -> new CoapOption(-1, new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
