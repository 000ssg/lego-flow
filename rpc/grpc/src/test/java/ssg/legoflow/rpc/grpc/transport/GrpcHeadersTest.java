package ssg.legoflow.rpc.grpc.transport;

import org.junit.jupiter.api.Test;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.rpc.grpc.metadata.Metadata;

import static org.assertj.core.api.Assertions.*;

class GrpcHeadersTest {

    @Test
    void testCreateRequestHeaders() {
        var headers = GrpcHeaders.createRequestHeaders(
                "/pkg.Svc/Method", "localhost:50051",
                GrpcEncoding.GZIP, GrpcTimeout.ofSeconds(5),
                new Metadata().put("x-custom", "value"));

        assertThat(headers.get(":method")).isEqualTo("POST");
        assertThat(headers.get(":path")).isEqualTo("/pkg.Svc/Method");
        assertThat(headers.get(":scheme")).isEqualTo("http");
        assertThat(headers.get(":authority")).isEqualTo("localhost:50051");
        assertThat(headers.get("content-type")).isEqualTo("application/grpc");
        assertThat(headers.get("te")).isEqualTo("trailers");
        assertThat(headers.get("grpc-encoding")).isEqualTo("gzip");
        assertThat(headers.get("grpc-timeout")).isEqualTo("5S");
        assertThat(headers.get("x-custom")).isEqualTo("value");
    }

    @Test
    void testCreateRequestHeadersMinimal() {
        var headers = GrpcHeaders.createRequestHeaders(
                "/Svc/Method", null, null, null, null);

        assertThat(headers.get(":method")).isEqualTo("POST");
        assertThat(headers.get(":path")).isEqualTo("/Svc/Method");
        assertThat(headers.get("content-type")).isEqualTo("application/grpc");
        assertThat(headers.get("te")).isEqualTo("trailers");
        assertThat(headers.get(":authority")).isNull();
        assertThat(headers.get("grpc-encoding")).isNull();
        assertThat(headers.get("grpc-timeout")).isNull();
    }

    @Test
    void testCreateResponseHeaders() {
        var headers = GrpcHeaders.createResponseHeaders(GrpcEncoding.GZIP);

        assertThat(headers.get(":status")).isEqualTo("200");
        assertThat(headers.get("content-type")).isEqualTo("application/grpc");
        assertThat(headers.get("grpc-encoding")).isEqualTo("gzip");
    }

    @Test
    void testCreateResponseHeadersIdentity() {
        var headers = GrpcHeaders.createResponseHeaders(GrpcEncoding.IDENTITY);

        assertThat(headers.get("grpc-encoding")).isNull();
    }

    @Test
    void testCreateTrailers() {
        var trailers = GrpcHeaders.createTrailers(GrpcStatus.OK, null, null);
        assertThat(trailers.get("grpc-status")).isEqualTo("0");
        assertThat(trailers.get("grpc-message")).isNull();
    }

    @Test
    void testCreateTrailersWithMessage() {
        var trailers = GrpcHeaders.createTrailers(GrpcStatus.NOT_FOUND,
                "Resource not found", null);
        assertThat(trailers.get("grpc-status")).isEqualTo("5");
        assertThat(trailers.get("grpc-message")).isNotNull();
    }

    @Test
    void testCreateTrailersWithMetadata() {
        var md = new Metadata().put("x-debug-id", "abc123");
        var trailers = GrpcHeaders.createTrailers(GrpcStatus.OK, null, md);
        assertThat(trailers.get("x-debug-id")).isEqualTo("abc123");
    }

    @Test
    void testExtractStatus() {
        var headers = new HttpHeaders();
        headers.set("grpc-status", "3");
        assertThat(GrpcHeaders.extractStatus(headers)).isEqualTo(GrpcStatus.INVALID_ARGUMENT);
    }

    @Test
    void testExtractStatusMissing() {
        var headers = new HttpHeaders();
        assertThat(GrpcHeaders.extractStatus(headers)).isEqualTo(GrpcStatus.UNKNOWN);
    }

    @Test
    void testExtractMessage() {
        var headers = new HttpHeaders();
        headers.set("grpc-message", "test%20message");
        assertThat(GrpcHeaders.extractMessage(headers)).isEqualTo("test message");
    }

    @Test
    void testExtractMetadata() {
        var headers = new HttpHeaders();
        headers.set(":status", "200");
        headers.set("content-type", "application/grpc");
        headers.set("grpc-status", "0");
        headers.set("x-custom-header", "value1");
        headers.set("x-another", "value2");

        var metadata = GrpcHeaders.extractMetadata(headers);
        assertThat(metadata.containsKey("x-custom-header")).isTrue();
        assertThat(metadata.containsKey("x-another")).isTrue();
        assertThat(metadata.containsKey(":status")).isFalse();
        assertThat(metadata.containsKey("content-type")).isFalse();
        assertThat(metadata.containsKey("grpc-status")).isFalse();
    }

    @Test
    void testIsGrpcContentType() {
        assertThat(GrpcHeaders.isGrpcContentType("application/grpc")).isTrue();
        assertThat(GrpcHeaders.isGrpcContentType("application/grpc+proto")).isTrue();
        assertThat(GrpcHeaders.isGrpcContentType("application/grpc+json")).isTrue();
        assertThat(GrpcHeaders.isGrpcContentType("application/json")).isFalse();
        assertThat(GrpcHeaders.isGrpcContentType(null)).isFalse();
    }

    @Test
    void testPercentEncodeRoundTrip() {
        String original = "Hello World! Special chars: /=+";
        String encoded = GrpcHeaders.percentEncode(original);
        String decoded = GrpcHeaders.percentDecode(encoded);
        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void testPercentEncodeAsciiLetters() {
        assertThat(GrpcHeaders.percentEncode("abc")).isEqualTo("abc");
    }

    @Test
    void testPercentEncodeSpace() {
        assertThat(GrpcHeaders.percentEncode("a b")).isEqualTo("a%20b");
    }
}
