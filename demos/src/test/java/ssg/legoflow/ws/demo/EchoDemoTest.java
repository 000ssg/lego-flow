package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.WebServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class EchoDemoTest {

    private HttpRouter router;

    @BeforeEach
    void setUp() {
        var registry = new WebServiceRegistry();
        registry.register(new EchoWebService());
        router = new HttpRouter();
        registry.installRoutes(router);
    }

    @Test
    void testEchoPostBody() {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.setBody(ByteBuffer.wrap("Hello Echo".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("Hello Echo");
    }

    @Test
    void testEchoPreservesContentType() {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
        request.setBody(ByteBuffer.wrap("{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("{\"key\":\"value\"}");
    }

    @Test
    void testEchoPlainText() {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");
        request.setBody(ByteBuffer.wrap("plain text body".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isEqualTo("text/plain");
        assertThat(response.getBodyAsString(StandardCharsets.UTF_8)).isEqualTo("plain text body");
    }

    @Test
    void testEchoEmptyBody() {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().remaining()).isEqualTo(0);
    }

    @Test
    void testEchoNoContentTypeHeader() {
        var request = HttpRequest.of(HttpMethod.POST, "/echo");
        request.setBody(ByteBuffer.wrap("no type".getBytes(StandardCharsets.UTF_8)));
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).isNull();
    }
}
