package ssg.legoflow.http2.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.stream.Http2Stream;
import ssg.legoflow.http2.stream.Http2StreamState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class Http2ServerTest {

    private Http2Server server;
    private Http2Connection connection;

    @BeforeEach
    void setUp() {
        server = new Http2Server(Http2Config.defaults());
        server.router().get("/hello", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello!"));
        server.router().post("/echo", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, req.getBodyAsString()));
        connection = server.acceptConnection();
    }

    @Test
    void testAcceptConnection() {
        assertThat(connection).isNotNull();
        assertThat(connection.isServer()).isTrue();
        assertThat(server.connections()).hasSize(1);
    }

    @Test
    void testHandleGetRequest() {
        var stream = prepareRequestStream(1, "GET", "/hello", null);
        var outFrames = server.handleRequest(connection, stream);

        assertThat(outFrames).isNotEmpty();
        assertThat(outFrames.getFirst().type()).isEqualTo(Http2FrameType.HEADERS);

        var responseHeaders = connection.decoder().decodeToHttpHeaders(outFrames.getFirst().payload());
        assertThat(responseHeaders.get(":status")).isEqualTo("200");

        var dataFrames = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.DATA)
                .toList();
        assertThat(dataFrames).isNotEmpty();

        var body = extractBody(dataFrames);
        assertThat(body).isEqualTo("Hello!");
    }

    @Test
    void testHandlePostRequest() {
        var stream = prepareRequestStream(1, "POST", "/echo", "echo this");
        var outFrames = server.handleRequest(connection, stream);

        var body = extractBody(outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.DATA)
                .toList());
        assertThat(body).isEqualTo("echo this");
    }

    @Test
    void testHandleNotFound() {
        var stream = prepareRequestStream(1, "GET", "/nonexistent", null);
        var outFrames = server.handleRequest(connection, stream);

        var responseHeaders = connection.decoder().decodeToHttpHeaders(outFrames.getFirst().payload());
        assertThat(responseHeaders.get(":status")).isEqualTo("404");
    }

    @Test
    void testHandleRequestStreamClosed() {
        var stream = prepareRequestStream(1, "GET", "/hello", null);
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);
        server.handleRequest(connection, stream);

        assertThat(stream.isClosed()).isTrue();
    }

    @Test
    void testRemoveConnection() {
        server.removeConnection(connection);
        assertThat(server.connections()).isEmpty();
    }

    @Test
    void testServerConfig() {
        assertThat(server.config()).isNotNull();
        assertThat(server.router()).isNotNull();
    }

    @Test
    void testHandlePushPromise() {
        var stream = prepareRequestStream(1, "GET", "/page", null);
        stream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var pushRequest = HttpRequest.of(HttpMethod.GET, "/style.css");
        pushRequest.getHeaders().set(HttpHeaders.HOST, "localhost");
        var pushResponse = HttpResponse.of(HttpStatus.OK, "body { }");

        var outFrames = server.handlePushPromise(connection, 1, pushRequest, pushResponse);

        var pushPromiseFrame = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.PUSH_PROMISE)
                .findFirst();
        assertThat(pushPromiseFrame).isPresent();

        var headersFrames = outFrames.stream()
                .filter(f -> f.type() == Http2FrameType.HEADERS)
                .toList();
        assertThat(headersFrames).isNotEmpty();
    }

    private Http2Stream prepareRequestStream(int streamId, String method, String path, String body) {
        var stream = connection.streamManager().getOrCreateStream(streamId);
        stream.transitionTo(Http2StreamState.OPEN);
        stream.headers().set(":method", method);
        stream.headers().set(":path", path);
        stream.headers().set(":scheme", "https");
        stream.headers().set(":authority", "localhost");
        if (body != null) {
            stream.addData(ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)));
        }
        return stream;
    }

    private String extractBody(java.util.List<Http2Frame> dataFrames) {
        var sb = new StringBuilder();
        for (var frame : dataFrames) {
            var payload = frame.payload();
            var bytes = new byte[payload.remaining()];
            payload.get(bytes);
            sb.append(new String(bytes, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }
}
