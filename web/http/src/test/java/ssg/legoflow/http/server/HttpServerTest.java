package ssg.legoflow.http.server;

import ssg.legoflow.blocks.DefaultContext;
import ssg.legoflow.http.config.ServerConfig;
import ssg.legoflow.http.config.StandardProfiles;
import ssg.legoflow.http.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class HttpServerTest {

    private HttpServer server;
    private DefaultContext ctx;

    @BeforeEach
    void setUp() {
        server = new HttpServer(new ServerConfig(StandardProfiles.serverStandard()));
        ctx = new DefaultContext();
    }

    @Test
    void testServerCreation() {
        assertThat(server.getRouter()).isNotNull();
        assertThat(server.getConfig()).isNotNull();
    }

    @Test
    void testServerWithCustomName() {
        var config = new ServerConfig(StandardProfiles.serverMinimal());
        var named = new HttpServer("my-server", config);

        assertThat(named.getConfig()).isSameAs(config);
    }

    @Test
    void testHandleRequestDispatches() {
        server.getRouter().get("/test", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "test response"));
        var request = HttpRequest.of(HttpMethod.GET, "/test");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBodyAsString()).isEqualTo("test response");
    }

    @Test
    void testHandleRequestReturns404ForUnknownRoute() {
        var request = HttpRequest.of(HttpMethod.GET, "/unknown");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testHandleRequestAppliesCompression() {
        server.getRouter().get("/big", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "data ".repeat(200)));
        var request = HttpRequest.of(HttpMethod.GET, "/big");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
        assertThat(response.getHeaders().get(HttpHeaders.VARY)).isEqualTo(HttpHeaders.ACCEPT_ENCODING);
    }

    @Test
    void testCompressionCanBeDisabled() {
        server.setCompressionEnabled(false);
        server.getRouter().get("/big", (httpCtx, req) ->
                HttpResponse.of(HttpStatus.OK, "data ".repeat(200)));
        var request = HttpRequest.of(HttpMethod.GET, "/big");
        request.getHeaders().set(HttpHeaders.ACCEPT_ENCODING, "gzip");

        var response = server.handleRequest(ctx, request);

        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING)).isNull();
    }

    @Test
    void testCompressionEnabledByDefault() {
        assertThat(server.isCompressionEnabled()).isTrue();
    }

    @Test
    void testHandlerExceptionPropagatesFromHandleRequest() {
        server.getRouter().get("/error", (httpCtx, req) -> {
            throw new RuntimeException("Runtime test error");
        });
        var request = HttpRequest.of(HttpMethod.GET, "/error");

        assertThatThrownBy(() -> server.handleRequest(ctx, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Runtime test error");
    }

    @Test
    void testServerPort() {
        var config = new ServerConfig(StandardProfiles.serverMinimal());
        config.setPort(9999);
        var s = new HttpServer(config);

        assertThat(s.getConfig().getPort()).isEqualTo(9999);
    }
}
