package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2ConnectionPreface;
import ssg.legoflow.http2.frame.*;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SimpleHttp2DemoTest {

    @Test
    void testSimpleGetRequest() {
        var demo = new SimpleHttp2Server();
        var serverConn = demo.server().acceptConnection();

        var preface = Http2ConnectionPreface.createClientPreface();
        var prefaceResponse = demo.handleClientPreface(serverConn, preface);
        assertThat(prefaceResponse).isNotEmpty();

        var clientConn = new Http2Connection(false);
        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/hello");
        headers.set(":scheme", "https");
        headers.set(":authority", "localhost");
        var encoded = clientConn.encoder().encode(headers);
        var headersFrame = Http2Frame.headers(1, encoded, true, true);

        var responseFrames = demo.processIncomingFrames(serverConn, List.of(headersFrame));

        var responseHeaderFrame = responseFrames.stream()
                .filter(f -> f.type() == Http2FrameType.HEADERS)
                .findFirst();
        assertThat(responseHeaderFrame).isPresent();

        var responseHeaders = serverConn.decoder().decodeToHttpHeaders(responseHeaderFrame.get().payload());
        assertThat(responseHeaders.get(":status")).isEqualTo("200");

        var body = extractBody(responseFrames);
        assertThat(body).isEqualTo("Hello, HTTP/2!");
    }

    @Test
    void testNotFoundRoute() {
        var demo = new SimpleHttp2Server();
        var serverConn = demo.server().acceptConnection();

        var clientConn = new Http2Connection(false);
        var headers = new HttpHeaders();
        headers.set(":method", "GET");
        headers.set(":path", "/unknown");
        headers.set(":scheme", "https");
        var encoded = clientConn.encoder().encode(headers);
        var headersFrame = Http2Frame.headers(1, encoded, true, true);

        var responseFrames = demo.processIncomingFrames(serverConn, List.of(headersFrame));

        var responseHeaders = serverConn.decoder().decodeToHttpHeaders(
                responseFrames.stream()
                        .filter(f -> f.type() == Http2FrameType.HEADERS)
                        .findFirst().get().payload());
        assertThat(responseHeaders.get(":status")).isEqualTo("404");
    }

    private String extractBody(List<Http2Frame> frames) {
        var sb = new StringBuilder();
        for (var f : frames) {
            if (f.type() == Http2FrameType.DATA) {
                var payload = f.payload();
                var bytes = new byte[payload.remaining()];
                payload.get(bytes);
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }
}
