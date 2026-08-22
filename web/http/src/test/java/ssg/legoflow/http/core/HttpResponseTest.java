package ssg.legoflow.http.core;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class HttpResponseTest {

    @Test
    void testFactoryMethodOfStatusOnly() {
        // When
        var response = HttpResponse.of(HttpStatus.OK);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders()).isNotNull();
    }

    @Test
    void testFactoryMethodOfWithBody() {
        // When
        var response = HttpResponse.of(HttpStatus.OK, "Hello World");

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("Hello World");
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_LENGTH))
                .isEqualTo(String.valueOf("Hello World".getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void testNotFoundResponse() {
        // When
        var response = HttpResponse.of(HttpStatus.NOT_FOUND, "Not Found");

        // Then
        assertThat(response.getStatus().code()).isEqualTo(404);
        assertThat(response.getStatus().reason()).isEqualTo("Not Found");
    }

    @Test
    void testSetBody() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);

        // When
        response.setBody(ByteBuffer.wrap("custom body".getBytes(StandardCharsets.UTF_8)));

        // Then
        assertThat(response.getBodyAsString()).isEqualTo("custom body");
    }

    @Test
    void testNoContentResponse() {
        // When
        var response = HttpResponse.of(HttpStatus.NO_CONTENT);

        // Then
        assertThat(response.getStatus().code()).isEqualTo(204);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void testContentLengthSetAutomatically() {
        // When
        var response = HttpResponse.of(HttpStatus.OK, "test");

        // Then
        assertThat(response.getContentLength()).isEqualTo(4);
    }

    @Test
    void testContentLengthMinusOneWhenMissing() {
        // Given
        var response = HttpResponse.of(HttpStatus.OK);

        // Then
        assertThat(response.getContentLength()).isEqualTo(-1);
    }

    @Test
    void testConstructorWithAllParameters() {
        // Given
        var headers = new HttpHeaders();
        headers.set("X-Custom", "value");

        // When
        var response = new HttpResponse(HttpStatus.CREATED, HttpVersion.HTTP_1_0, headers);

        // Then
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);
        assertThat(response.getHeaders().get("x-custom")).isEqualTo("value");
    }
}
