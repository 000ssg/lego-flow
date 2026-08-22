package ssg.legoflow.http.core;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class HttpStatusTest {

    @Test
    void testFromCodeOk() {
        // When
        var status = HttpStatus.fromCode(200);

        // Then
        assertThat(status).isEqualTo(HttpStatus.OK);
        assertThat(status.code()).isEqualTo(200);
        assertThat(status.reason()).isEqualTo("OK");
    }

    @Test
    void testFromCodeNotFound() {
        // When
        var status = HttpStatus.fromCode(404);

        // Then
        assertThat(status).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status.reason()).isEqualTo("Not Found");
    }

    @Test
    void testFromCodeUnknownThrows() {
        // When/Then
        assertThatThrownBy(() -> HttpStatus.fromCode(999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("999");
    }

    @Test
    void testInformationalCodes() {
        assertThat(HttpStatus.CONTINUE.code()).isEqualTo(100);
        assertThat(HttpStatus.SWITCHING_PROTOCOLS.code()).isEqualTo(101);
    }

    @Test
    void testSuccessCodes() {
        assertThat(HttpStatus.OK.code()).isEqualTo(200);
        assertThat(HttpStatus.CREATED.code()).isEqualTo(201);
        assertThat(HttpStatus.NO_CONTENT.code()).isEqualTo(204);
        assertThat(HttpStatus.PARTIAL_CONTENT.code()).isEqualTo(206);
    }

    @Test
    void testRedirectionCodes() {
        assertThat(HttpStatus.MOVED_PERMANENTLY.code()).isEqualTo(301);
        assertThat(HttpStatus.FOUND.code()).isEqualTo(302);
        assertThat(HttpStatus.NOT_MODIFIED.code()).isEqualTo(304);
        assertThat(HttpStatus.TEMPORARY_REDIRECT.code()).isEqualTo(307);
    }

    @Test
    void testClientErrorCodes() {
        assertThat(HttpStatus.BAD_REQUEST.code()).isEqualTo(400);
        assertThat(HttpStatus.UNAUTHORIZED.code()).isEqualTo(401);
        assertThat(HttpStatus.FORBIDDEN.code()).isEqualTo(403);
        assertThat(HttpStatus.NOT_FOUND.code()).isEqualTo(404);
        assertThat(HttpStatus.METHOD_NOT_ALLOWED.code()).isEqualTo(405);
    }

    @Test
    void testServerErrorCodes() {
        assertThat(HttpStatus.INTERNAL_SERVER_ERROR.code()).isEqualTo(500);
        assertThat(HttpStatus.BAD_GATEWAY.code()).isEqualTo(502);
        assertThat(HttpStatus.SERVICE_UNAVAILABLE.code()).isEqualTo(503);
        assertThat(HttpStatus.GATEWAY_TIMEOUT.code()).isEqualTo(504);
    }
}
