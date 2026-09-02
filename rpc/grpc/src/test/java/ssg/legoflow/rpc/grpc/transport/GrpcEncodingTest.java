package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class GrpcEncodingTest {

    @Test
    void testIdentityValue() {
        assertThat(GrpcEncoding.IDENTITY.value()).isEqualTo("identity");
    }

    @Test
    void testGzipValue() {
        assertThat(GrpcEncoding.GZIP.value()).isEqualTo("gzip");
    }

    @Test
    void testDeflateValue() {
        assertThat(GrpcEncoding.DEFLATE.value()).isEqualTo("deflate");
    }

    @Test
    void testFromValueGzip() {
        assertThat(GrpcEncoding.fromValue("gzip")).isEqualTo(GrpcEncoding.GZIP);
    }

    @Test
    void testFromValueIdentity() {
        assertThat(GrpcEncoding.fromValue("identity")).isEqualTo(GrpcEncoding.IDENTITY);
    }

    @Test
    void testFromValueDeflate() {
        assertThat(GrpcEncoding.fromValue("deflate")).isEqualTo(GrpcEncoding.DEFLATE);
    }

    @Test
    void testFromValueNull() {
        assertThat(GrpcEncoding.fromValue(null)).isEqualTo(GrpcEncoding.IDENTITY);
    }

    @Test
    void testFromValueEmpty() {
        assertThat(GrpcEncoding.fromValue("")).isEqualTo(GrpcEncoding.IDENTITY);
    }

    @Test
    void testFromValueCaseInsensitive() {
        assertThat(GrpcEncoding.fromValue("GZIP")).isEqualTo(GrpcEncoding.GZIP);
    }

    @Test
    void testFromValueInvalid() {
        assertThatThrownBy(() -> GrpcEncoding.fromValue("snappy"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
