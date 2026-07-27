package ssg.legoflow.http.transfer;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpStatus;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MultipartByteRangeHandlerTest {

    @Test
    void testGenerateBoundary() {
        // When
        String boundary1 = MultipartByteRangeHandler.generateBoundary();
        String boundary2 = MultipartByteRangeHandler.generateBoundary();

        // Then
        assertThat(boundary1).startsWith("ByteRangeBoundary_");
        assertThat(boundary1).isNotEqualTo(boundary2);
    }

    @Test
    void testBuildMultipartResponse() {
        // Given
        var content = ByteBuffer.wrap("Hello, World! This is test content.".getBytes(StandardCharsets.UTF_8));
        var ranges = List.of(
                new ByteRangeHandler.ByteRange(0, 4),    // "Hello"
                new ByteRangeHandler.ByteRange(7, 11)    // "World"
        );

        // When
        var response = MultipartByteRangeHandler.buildMultipartResponse(
                content, ranges, 35, "text/plain");

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.PARTIAL_CONTENT);
        String contentType = response.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        assertThat(contentType).startsWith("multipart/byteranges; boundary=");
    }

    @Test
    void testBuildMultipartBody() {
        // Given
        var content = ByteBuffer.wrap("ABCDEFGHIJ".getBytes(StandardCharsets.UTF_8));
        var ranges = List.of(
                new ByteRangeHandler.ByteRange(0, 2),    // "ABC"
                new ByteRangeHandler.ByteRange(7, 9)     // "HIJ"
        );
        String boundary = "testboundary";

        // When
        byte[] body = MultipartByteRangeHandler.buildMultipartBody(
                content, ranges, 10, "text/plain", boundary);
        String bodyStr = new String(body, StandardCharsets.UTF_8);

        // Then
        assertThat(bodyStr).contains("--testboundary");
        assertThat(bodyStr).contains("Content-Type: text/plain");
        assertThat(bodyStr).contains("Content-Range: bytes 0-2/10");
        assertThat(bodyStr).contains("Content-Range: bytes 7-9/10");
        assertThat(bodyStr).contains("--testboundary--");
    }

    @Test
    void testParseMultipartBody() {
        // Given
        String boundary = "myboundary";
        String body = "--myboundary\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Range: bytes 0-2/10\r\n" +
                "\r\n" +
                "ABC\r\n" +
                "--myboundary\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Range: bytes 7-9/10\r\n" +
                "\r\n" +
                "HIJ\r\n" +
                "--myboundary--\r\n";

        // When
        List<byte[]> parts = MultipartByteRangeHandler.parseMultipartBody(
                body.getBytes(StandardCharsets.UTF_8), boundary);

        // Then
        assertThat(parts).hasSize(2);
        assertThat(new String(parts.get(0), StandardCharsets.UTF_8)).isEqualTo("ABC");
        assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).isEqualTo("HIJ");
    }

    @Test
    void testExtractBoundary() {
        // When
        String boundary = MultipartByteRangeHandler.extractBoundary(
                "multipart/byteranges; boundary=myboundary");

        // Then
        assertThat(boundary).isEqualTo("myboundary");
    }

    @Test
    void testExtractBoundaryNull() {
        assertThat(MultipartByteRangeHandler.extractBoundary(null)).isNull();
        assertThat(MultipartByteRangeHandler.extractBoundary("text/plain")).isNull();
    }

    @Test
    void testRoundTrip() {
        // Given
        var content = ByteBuffer.wrap("Hello, World!".getBytes(StandardCharsets.UTF_8));
        var ranges = List.of(
                new ByteRangeHandler.ByteRange(0, 4),
                new ByteRangeHandler.ByteRange(7, 11)
        );
        String boundary = "roundtrip";

        // When — build then parse
        byte[] body = MultipartByteRangeHandler.buildMultipartBody(
                content, ranges, 13, "text/plain", boundary);
        List<byte[]> parts = MultipartByteRangeHandler.parseMultipartBody(body, boundary);

        // Then
        assertThat(parts).hasSize(2);
        assertThat(new String(parts.get(0), StandardCharsets.UTF_8)).isEqualTo("Hello");
        assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).isEqualTo("World");
    }

    @Test
    void testThreeRanges() {
        // Given
        var content = ByteBuffer.wrap("ABCDEFGHIJKLMNOP".getBytes(StandardCharsets.UTF_8));
        var ranges = List.of(
                new ByteRangeHandler.ByteRange(0, 2),
                new ByteRangeHandler.ByteRange(5, 7),
                new ByteRangeHandler.ByteRange(10, 12)
        );
        String boundary = "triple";

        // When
        byte[] body = MultipartByteRangeHandler.buildMultipartBody(
                content, ranges, 16, "application/octet-stream", boundary);
        List<byte[]> parts = MultipartByteRangeHandler.parseMultipartBody(body, boundary);

        // Then
        assertThat(parts).hasSize(3);
        assertThat(new String(parts.get(0), StandardCharsets.UTF_8)).isEqualTo("ABC");
        assertThat(new String(parts.get(1), StandardCharsets.UTF_8)).isEqualTo("FGH");
        assertThat(new String(parts.get(2), StandardCharsets.UTF_8)).isEqualTo("KLM");
    }
}
