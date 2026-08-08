package ssg.legoflow.ws.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.ws.WebServiceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ContentNegotiationDemoTest {

    private HttpRouter router;

    @BeforeEach
    void setUp() {
        var registry = new WebServiceRegistry();
        registry.register(new MultiFormatService());
        router = new HttpRouter();
        registry.installRoutes(router);
    }

    @Test
    void testJsonResponse() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        request.getHeaders().set(HttpHeaders.ACCEPT, "application/json");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains("application/json");
        var body = response.getBodyAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"service\":\"MultiFormat\"");
    }

    @Test
    void testXmlResponse() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/xml");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains("text/xml");
        var body = response.getBodyAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("<info>").contains("<service>MultiFormat</service>");
    }

    @Test
    void testPlainTextResponse() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        request.getHeaders().set(HttpHeaders.ACCEPT, "text/plain");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains("text/plain");
        var body = response.getBodyAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("service=MultiFormat");
    }

    @Test
    void testUnsupportedMediaType() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        request.getHeaders().set(HttpHeaders.ACCEPT, "image/png");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void testNoAcceptHeaderDefaultsToJson() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }

    @Test
    void testWildcardAcceptReturnsJson() {
        var request = HttpRequest.of(HttpMethod.GET, "/info");
        request.getHeaders().set(HttpHeaders.ACCEPT, "*/*");
        var response = router.dispatch(null, request);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_TYPE)).contains("application/json");
    }
}
