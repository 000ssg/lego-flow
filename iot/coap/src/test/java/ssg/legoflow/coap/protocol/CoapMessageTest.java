package ssg.legoflow.coap.protocol;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CoapMessage}.
 *
 * @since 0.1.0
 */
class CoapMessageTest {

    @Test
    void testBuilderDefaults() {
        var msg = CoapMessage.builder().build();

        assertThat(msg.version()).isEqualTo(CoapVersion.V1);
        assertThat(msg.type()).isEqualTo(CoapType.CONFIRMABLE);
        assertThat(msg.code()).isEqualTo(CoapCode.EMPTY);
        assertThat(msg.messageId()).isZero();
        assertThat(msg.token()).isEmpty();
        assertThat(msg.options()).isEmpty();
        assertThat(msg.payload()).isEmpty();
    }

    @Test
    void testBuilderWithAllFields() {
        var token = new byte[]{0x01, 0x02, 0x03, 0x04};
        var payload = "Hello CoAP".getBytes(StandardCharsets.UTF_8);

        var msg = CoapMessage.builder()
                .version(CoapVersion.V1)
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(12345)
                .token(token)
                .uriPath("/sensors/temperature")
                .payload(payload)
                .build();

        assertThat(msg.type()).isEqualTo(CoapType.NON_CONFIRMABLE);
        assertThat(msg.code()).isEqualTo(CoapCode.GET);
        assertThat(msg.messageId()).isEqualTo(12345);
        assertThat(msg.token()).containsExactly(0x01, 0x02, 0x03, 0x04);
        assertThat(msg.payload()).isEqualTo(payload);
    }

    @Test
    void testGetUriPath() {
        var msg = CoapMessage.builder()
                .uriPath("/sensors/temperature")
                .build();

        assertThat(msg.getUriPath()).isEqualTo("/sensors/temperature");
    }

    @Test
    void testGetUriPathEmpty() {
        var msg = CoapMessage.builder().build();

        assertThat(msg.getUriPath()).isEqualTo("/");
    }

    @Test
    void testGetUriQuery() {
        var msg = CoapMessage.builder()
                .uriQuery("key=value")
                .uriQuery("other=123")
                .build();

        assertThat(msg.getUriQuery()).isEqualTo("key=value&other=123");
    }

    @Test
    void testGetContentFormat() {
        var msg = CoapMessage.builder()
                .contentFormat(ContentFormat.APPLICATION_JSON.value())
                .build();

        assertThat(msg.getContentFormat()).isEqualTo(ContentFormat.APPLICATION_JSON.value());
    }

    @Test
    void testGetContentFormatAbsent() {
        var msg = CoapMessage.builder().build();

        assertThat(msg.getContentFormat()).isEqualTo(-1);
    }

    @Test
    void testGetETag() {
        var etag = new byte[]{0x01, 0x02, 0x03};
        var msg = CoapMessage.builder()
                .option(CoapOption.etag(etag))
                .build();

        assertThat(msg.getETag()).containsExactly(0x01, 0x02, 0x03);
    }

    @Test
    void testGetLocationPath() {
        var msg = CoapMessage.builder()
                .option(CoapOption.locationPath("items"))
                .option(CoapOption.locationPath("42"))
                .build();

        assertThat(msg.getLocationPath()).isEqualTo("/items/42");
    }

    @Test
    void testTokenDefensiveCopy() {
        var token = new byte[]{0x01, 0x02};
        var msg = CoapMessage.builder().token(token).build();

        // Modifying the original should not affect the message
        token[0] = (byte) 0xFF;
        assertThat(msg.token()[0]).isEqualTo((byte) 0x01);
    }
}
