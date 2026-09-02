package ssg.legoflow.http2.hpack;

import ssg.legoflow.http.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HpackDecoderTest {

    @Test
    void testDecodeEncodedHeaders() {
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/");
        headers.set(":scheme", "https");

        var encoded = encoder.encode(headers);
        var decoded = decoder.decode(encoded);

        assertThat(decoded).hasSize(3);
        assertThat(decoded.get(0).name()).isEqualTo(":method");
        assertThat(decoded.get(0).value()).isEqualTo("GET");
        assertThat(decoded.get(1).name()).isEqualTo(":path");
        assertThat(decoded.get(1).value()).isEqualTo("/");
    }

    @Test
    void testDecodeToHttpHeaders() {
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        var headers = new HttpHeaders();
        headers.set(":method", "POST");
        headers.set("content-type", "application/json");

        var encoded = encoder.encode(headers);
        var decoded = decoder.decodeToHttpHeaders(encoded);

        assertThat(decoded.get(":method")).isEqualTo("POST");
        assertThat(decoded.get("content-type")).isEqualTo("application/json");
    }

    @Test
    void testDynamicTableUpdate() {
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        var headers = new HttpHeaders();
        headers.set("x-custom", "first");

        var encoded = encoder.encode(headers);
        decoder.decode(encoded);

        assertThat(decoder.getDynamicTable().size()).isEqualTo(1);
    }

    @Test
    void testDecodeMultipleRequests() {
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        var headers1 = new HttpHeaders();
        headers1.set(":method", "GET");
        headers1.set(":path", "/first");
        var encoded1 = encoder.encode(headers1);
        var decoded1 = decoder.decode(encoded1);

        assertThat(decoded1).hasSizeGreaterThanOrEqualTo(2);

        var headers2 = new HttpHeaders();
        headers2.set(":method", "GET");
        headers2.set(":path", "/second");
        var encoded2 = encoder.encode(headers2);
        var decoded2 = decoder.decode(encoded2);

        assertThat(decoded2).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void testSetMaxTableSize() {
        var decoder = new HpackDecoder();
        decoder.setMaxTableSize(256);
        assertThat(decoder.getDynamicTable().maxSize()).isEqualTo(256);
    }

    @Test
    void testIntegerDecoding() {
        var out = new java.io.ByteArrayOutputStream();
        HpackEncoder.encodeInteger(out, 1337, 5, 0x00);
        var buf = java.nio.ByteBuffer.wrap(out.toByteArray());
        int decoded = HpackDecoder.decodeInteger(buf, 5);
        assertThat(decoded).isEqualTo(1337);
    }

    @Test
    void testSmallIntegerDecoding() {
        var out = new java.io.ByteArrayOutputStream();
        HpackEncoder.encodeInteger(out, 10, 5, 0x00);
        var buf = java.nio.ByteBuffer.wrap(out.toByteArray());
        int decoded = HpackDecoder.decodeInteger(buf, 5);
        assertThat(decoded).isEqualTo(10);
    }

    @Test
    void testRoundTripWithCustomHeaders() {
        var encoder = new HpackEncoder();
        var decoder = new HpackDecoder();

        var headers = new HttpHeaders();
        headers.set(":status", "200");
        headers.set("content-type", "text/html");
        headers.set("x-custom-header", "custom-value");

        var encoded = encoder.encode(headers);
        var decoded = decoder.decodeToHttpHeaders(encoded);

        assertThat(decoded.get(":status")).isEqualTo("200");
        assertThat(decoded.get("content-type")).isEqualTo("text/html");
        assertThat(decoded.get("x-custom-header")).isEqualTo("custom-value");
    }
}
