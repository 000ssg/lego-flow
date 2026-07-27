package ssg.legoflow.http.header;

import ssg.legoflow.http.core.HttpHeaders;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HttpHeadersTest {

    @Test
    void testCaseInsensitiveGet() {
        // Given
        var headers = new HttpHeaders();
        headers.set("Content-Type", "text/html");

        // Then
        assertThat(headers.get("content-type")).isEqualTo("text/html");
        assertThat(headers.get("CONTENT-TYPE")).isEqualTo("text/html");
        assertThat(headers.get("Content-Type")).isEqualTo("text/html");
    }

    @Test
    void testSetOverwritesPreviousValue() {
        // Given
        var headers = new HttpHeaders();
        headers.set("Accept", "text/html");

        // When
        headers.set("Accept", "application/json");

        // Then
        assertThat(headers.get("accept")).isEqualTo("application/json");
        assertThat(headers.getAll("accept")).containsExactly("application/json");
    }

    @Test
    void testAddAppendsMultipleValues() {
        // Given
        var headers = new HttpHeaders();
        headers.add("Accept-Encoding", "gzip");
        headers.add("Accept-Encoding", "deflate");

        // Then
        assertThat(headers.getAll("accept-encoding")).containsExactly("gzip", "deflate");
        assertThat(headers.get("accept-encoding")).isEqualTo("gzip");
    }

    @Test
    void testRemove() {
        // Given
        var headers = new HttpHeaders();
        headers.set("X-Custom", "value");

        // When
        headers.remove("X-Custom");

        // Then
        assertThat(headers.contains("x-custom")).isFalse();
        assertThat(headers.get("x-custom")).isNull();
    }

    @Test
    void testContains() {
        // Given
        var headers = new HttpHeaders();
        headers.set("Host", "example.com");

        // Then
        assertThat(headers.contains("host")).isTrue();
        assertThat(headers.contains("HOST")).isTrue();
        assertThat(headers.contains("x-missing")).isFalse();
    }

    @Test
    void testNames() {
        // Given
        var headers = new HttpHeaders();
        headers.set("Host", "example.com");
        headers.set("Accept", "text/html");

        // Then
        assertThat(headers.names()).containsExactlyInAnyOrder("host", "accept");
    }

    @Test
    void testSizeAndEmpty() {
        // Given
        var headers = new HttpHeaders();

        // Then
        assertThat(headers.isEmpty()).isTrue();
        assertThat(headers.size()).isZero();

        // When
        headers.set("Host", "example.com");

        // Then
        assertThat(headers.isEmpty()).isFalse();
        assertThat(headers.size()).isEqualTo(1);
    }

    @Test
    void testGetAllReturnsEmptyListForMissingHeader() {
        // Given
        var headers = new HttpHeaders();

        // Then
        assertThat(headers.getAll("missing")).isEmpty();
    }
}
