package ssg.legoflow.coap.codec;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.CoapVersion;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link CoapCodec}.
 *
 * @since 0.1.0
 */
class CoapCodecTest {

    private final CoapCodec codec = new CoapCodec();

    @Test
    void testEncodeDecodeEmptyMessage() {
        var msg = CoapMessage.builder()
                .type(CoapType.ACKNOWLEDGEMENT)
                .code(CoapCode.EMPTY)
                .messageId(42)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.version()).isEqualTo(CoapVersion.V1);
        assertThat(decoded.type()).isEqualTo(CoapType.ACKNOWLEDGEMENT);
        assertThat(decoded.code()).isEqualTo(CoapCode.EMPTY);
        assertThat(decoded.messageId()).isEqualTo(42);
        assertThat(decoded.token()).isEmpty();
        assertThat(decoded.payload()).isEmpty();
    }

    @Test
    void testEncodeDecodeGetRequest() {
        var token = new byte[]{0x01, 0x02, 0x03, 0x04};
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1234)
                .token(token)
                .uriPath("/sensors/temperature")
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.type()).isEqualTo(CoapType.CONFIRMABLE);
        assertThat(decoded.code()).isEqualTo(CoapCode.GET);
        assertThat(decoded.messageId()).isEqualTo(1234);
        assertThat(decoded.token()).containsExactly(0x01, 0x02, 0x03, 0x04);
        assertThat(decoded.getUriPath()).isEqualTo("/sensors/temperature");
    }

    @Test
    void testEncodeDecodeResponseWithPayload() {
        var payload = "22.5".getBytes(StandardCharsets.UTF_8);
        var msg = CoapMessage.builder()
                .type(CoapType.ACKNOWLEDGEMENT)
                .code(CoapCode.CONTENT)
                .messageId(1234)
                .token(new byte[]{0x01})
                .contentFormat(ContentFormat.TEXT_PLAIN.value())
                .payload(payload)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(decoded.getContentFormat()).isEqualTo(ContentFormat.TEXT_PLAIN.value());
        assertThat(decoded.payload()).isEqualTo(payload);
        assertThat(decoded.getPayloadString()).isEqualTo("22.5");
    }

    @Test
    void testEncodeDecodeNonConfirmable() {
        var msg = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(5678)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.type()).isEqualTo(CoapType.NON_CONFIRMABLE);
    }

    @Test
    void testEncodeDecodeReset() {
        var msg = CoapMessage.builder()
                .type(CoapType.RESET)
                .code(CoapCode.EMPTY)
                .messageId(9999)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.type()).isEqualTo(CoapType.RESET);
        assertThat(decoded.code()).isEqualTo(CoapCode.EMPTY);
    }

    @Test
    void testEncodeDecodeWithMultipleOptions() {
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(100)
                .uriPath("/sensors/temperature")
                .uriQuery("unit=celsius")
                .option(CoapOption.accept(ContentFormat.TEXT_PLAIN.value()))
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.getUriPath()).isEqualTo("/sensors/temperature");
        assertThat(decoded.getUriQuery()).isEqualTo("unit=celsius");
        assertThat(decoded.getOption(CoapOption.ACCEPT)).isNotNull();
    }

    @Test
    void testEncodeDecodePostWithPayload() {
        var payload = "{\"value\":42}".getBytes(StandardCharsets.UTF_8);
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(200)
                .token(new byte[]{0x0A, 0x0B})
                .uriPath("/items")
                .contentFormat(ContentFormat.APPLICATION_JSON.value())
                .payload(payload)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.POST);
        assertThat(decoded.getUriPath()).isEqualTo("/items");
        assertThat(decoded.getContentFormat()).isEqualTo(ContentFormat.APPLICATION_JSON.value());
        assertThat(decoded.payload()).isEqualTo(payload);
    }

    @Test
    void testEncodeDecodePutRequest() {
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.PUT)
                .messageId(300)
                .uriPath("/items/1")
                .payload("updated value")
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.PUT);
        assertThat(decoded.getPayloadString()).isEqualTo("updated value");
    }

    @Test
    void testEncodeDecodeDeleteRequest() {
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.DELETE)
                .messageId(400)
                .uriPath("/items/1")
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.DELETE);
        assertThat(decoded.getUriPath()).isEqualTo("/items/1");
    }

    @Test
    void testEncodeDecodeNotFoundResponse() {
        var msg = CoapMessage.builder()
                .type(CoapType.ACKNOWLEDGEMENT)
                .code(CoapCode.NOT_FOUND)
                .messageId(500)
                .payload("Resource not found")
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.NOT_FOUND);
        assertThat(decoded.code().isClientError()).isTrue();
    }

    @Test
    void testEncodeDecodeWithMaxToken() {
        var token = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(600)
                .token(token)
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.token()).hasSize(8).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void testDecodeBufferTooShortThrows() {
        var buffer = ByteBuffer.allocate(2);
        buffer.putShort((short) 0);
        buffer.flip();

        assertThatThrownBy(() -> codec.decode(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void testHeaderBitLayout() {
        // Manually verify the header layout: V1(01) + CON(00) + TKL=4(0100) + GET(00000001) + MID(0x1234)
        var msg = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(0x1234)
                .token(new byte[]{0x01, 0x02, 0x03, 0x04})
                .build();

        var buffer = codec.encode(msg);
        byte firstByte = buffer.get(0);

        // Version=1 (bits 7-6): 01
        assertThat((firstByte >> 6) & 0x03).isEqualTo(1);
        // Type=CON (bits 5-4): 00
        assertThat((firstByte >> 4) & 0x03).isZero();
        // TKL=4 (bits 3-0): 0100
        assertThat(firstByte & 0x0F).isEqualTo(4);
    }

    @Test
    void testEncodeDecodeWithEtag() {
        var etag = new byte[]{0x11, 0x22, 0x33};
        var msg = CoapMessage.builder()
                .type(CoapType.ACKNOWLEDGEMENT)
                .code(CoapCode.VALID)
                .messageId(700)
                .option(CoapOption.etag(etag))
                .build();

        var buffer = codec.encode(msg);
        var decoded = codec.decode(buffer);

        assertThat(decoded.code()).isEqualTo(CoapCode.VALID);
        assertThat(decoded.getETag()).containsExactly(0x11, 0x22, 0x33);
    }

    @Test
    void testEncodeDecodeRoundTripPreservesMessageId() {
        for (int mid = 0; mid < 65536; mid += 1000) {
            var msg = CoapMessage.builder().messageId(mid).build();
            var buffer = codec.encode(msg);
            var decoded = codec.decode(buffer);
            assertThat(decoded.messageId()).isEqualTo(mid);
        }
    }
}
