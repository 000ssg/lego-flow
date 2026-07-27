package ssg.legoflow.http3.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class Http3ServerTest {

    private Http3Server server;

    @BeforeEach
    void setUp() {
        server = new Http3Server(Http3Config.defaults());
        server.router().get("/hello", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello, HTTP/3!"));
        server.router().get("/json", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "{\"message\":\"ok\"}");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "application/json");
            return response;
        });
    }

    @Test
    void testAcceptConnection() {
        // Given
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();

        // When
        var h3Conn = server.acceptConnection(quicConn);

        // Then
        assertThat(h3Conn).isNotNull();
        assertThat(h3Conn.isConnected()).isTrue();
        assertThat(server.getActiveConnections()).hasSize(1);
    }

    @Test
    void testHandleRequest() {
        // Given
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        var h3Conn = server.acceptConnection(quicConn);

        var stream = quicConn.createStream(true);
        var headers = List.<Map.Entry<String, String>>of(
                new AbstractMap.SimpleEntry<>(":method", "GET"),
                new AbstractMap.SimpleEntry<>(":path", "/hello"),
                new AbstractMap.SimpleEntry<>(":scheme", "https"),
                new AbstractMap.SimpleEntry<>(":authority", "localhost")
        );

        // When: handle the request (sends response on the stream)
        server.handleRequest(h3Conn, stream, headers, null);

        // Then: no exception thrown — request was routed successfully
        assertThat(server.getActiveConnections()).hasSize(1);
    }

    @Test
    void testServerPush() {
        // Given
        var pushConfig = Http3Config.defaults().enablePush(true);
        var pushServer = new Http3Server(pushConfig);
        pushServer.router().get("/page", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "<html></html>"));

        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        var h3Conn = pushServer.acceptConnection(quicConn);
        var parentStream = quicConn.createStream(true);

        var pushRequest = HttpRequest.of(HttpMethod.GET, "/style.css");
        pushRequest.getHeaders().set(HttpHeaders.HOST, "localhost");
        var pushResponse = HttpResponse.of(HttpStatus.OK, "body { color: black; }");

        // When: push should not throw
        pushServer.handlePushPromise(h3Conn, parentStream, 0, pushRequest, pushResponse);

        // Then
        assertThat(pushServer.getActiveConnections()).hasSize(1);
    }

    @Test
    void testStopServer() {
        // Given
        var quicSettings = QuicSettings.builder()
                .initialMaxStreamsUni(10)
                .initialMaxStreamsBidi(10)
                .build();
        var quicConn = new QuicConnection(1L, true, quicSettings);
        quicConn.accept();
        server.acceptConnection(quicConn);
        assertThat(server.getActiveConnections()).hasSize(1);

        // When
        server.stop();

        // Then
        assertThat(server.getActiveConnections()).isEmpty();
    }

    @Test
    void testRouter() {
        // Given/When/Then
        assertThat(server.router()).isNotNull();
        assertThat(server.router().getRegisteredPaths()).contains("/hello", "/json");
    }

    @Test
    void testConfig() {
        // Given/When/Then
        assertThat(server.config()).isNotNull();
    }

    @Test
    void testServerWithRouter() {
        // Given
        var router = new ssg.legoflow.http.server.HttpRouter();
        router.get("/test", (ctx, req) -> HttpResponse.of(HttpStatus.OK, "test"));

        // When
        var customServer = new Http3Server(router, Http3Config.defaults());

        // Then
        assertThat(customServer.router().getRegisteredPaths()).contains("/test");
    }
}
