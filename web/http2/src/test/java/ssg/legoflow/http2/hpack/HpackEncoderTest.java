package ssg.legoflow.http2.hpack;

import ssg.legoflow.http.core.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HpackEncoderTest {

    @Test
    void testEncodeStaticTableMatch() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set(":method", "GET");

        var encoded = encoder.encode(headers);

        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeHeaderListPairs() {
        var encoder = new HpackEncoder();
        var encoded = encoder.encodeHeaderList(
                ":method", "GET",
                ":path", "/",
                ":scheme", "https"
        );

        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testEncodeDynamicTableUsage() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set("x-custom", "value1");

        encoder.encode(headers);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(1);

        headers.set("x-custom", "value1");
        encoder.encode(headers);
    }

    @Test
    void testEncodeMultipleHeaders() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/");
        headers.set(":scheme", "https");
        headers.set(":authority", "www.example.com");

        var encoded = encoder.encode(headers);
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testOddPairsThrowsException() {
        var encoder = new HpackEncoder();
        assertThatThrownBy(() -> encoder.encodeHeaderList(":method"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testSetMaxTableSize() {
        var encoder = new HpackEncoder();
        encoder.setMaxTableSize(256);
        assertThat(encoder.getDynamicTable().maxSize()).isEqualTo(256);
    }

    @Test
    void testEncodeWithoutHuffman() {
        var encoder = new HpackEncoder();
        encoder.setUseHuffman(false);

        var headers = new HttpHeaders();
        headers.set("x-custom", "value");

        var encoded = encoder.encode(headers);
        assertThat(encoded.remaining()).isGreaterThan(0);
    }

    @Test
    void testIntegerEncoding() {
        var out = new java.io.ByteArrayOutputStream();
        HpackEncoder.encodeInteger(out, 10, 5, 0x00);
        assertThat(out.toByteArray()[0] & 0xFF).isEqualTo(10);
    }

    @Test
    void testIntegerEncodingLargeValue() {
        var out = new java.io.ByteArrayOutputStream();
        HpackEncoder.encodeInteger(out, 1337, 5, 0x00);
        assertThat(out.size()).isGreaterThan(1);
    }

    // ---- Sensitive header (never-indexed) tests ----

    @Test
    void testDefaultSensitiveHeaders() {
        var encoder = new HpackEncoder();
        assertThat(encoder.isSensitive("authorization")).isTrue();
        assertThat(encoder.isSensitive("cookie")).isTrue();
        assertThat(encoder.isSensitive("set-cookie")).isTrue();
        assertThat(encoder.isSensitive("proxy-authorization")).isTrue();
        assertThat(encoder.isSensitive("x-custom")).isFalse();
    }

    @Test
    void testSensitiveHeaderEncodedAsNeverIndexed() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set("authorization", "Bearer secret-token");

        int dynamicBefore = encoder.getDynamicTable().size();
        var encoded = encoder.encode(headers);

        // Sensitive headers should NOT be added to the dynamic table
        assertThat(encoder.getDynamicTable().size()).isEqualTo(dynamicBefore);
        // The encoding should start with the 0x10 prefix (never-indexed)
        var bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(bytes[0] & 0xF0).isEqualTo(0x10);
    }

    @Test
    void testNonSensitiveHeaderAddedToDynamicTable() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set("x-custom", "value");

        encoder.encode(headers);
        assertThat(encoder.getDynamicTable().size()).isEqualTo(1);
    }

    @Test
    void testCustomSensitiveHeaders() {
        var encoder = new HpackEncoder();
        encoder.setSensitiveHeaders(java.util.Set.of("x-api-key", "x-secret"));

        assertThat(encoder.isSensitive("x-api-key")).isTrue();
        assertThat(encoder.isSensitive("x-secret")).isTrue();
        assertThat(encoder.isSensitive("authorization")).isFalse(); // Custom set replaces defaults
    }

    @Test
    void testCookieHeaderNeverIndexed() {
        var encoder = new HpackEncoder();
        var headers = new HttpHeaders();
        headers.set("cookie", "session=abc123");

        var encoded = encoder.encode(headers);
        var bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        assertThat(bytes[0] & 0xF0).isEqualTo(0x10);
    }
}
