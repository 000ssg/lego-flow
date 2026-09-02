package ssg.legoflow.http3.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicStream;
import ssg.legoflow.http3.server.Http3Server;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
/**
 * Demonstrates HTTP/3 server push with PUSH_PROMISE frames.
 *
 * <p>When a client requests a page, the server can proactively push
 * associated resources (e.g., stylesheets) using PUSH_PROMISE,
 * eliminating the round trip needed for the client to discover them.</p>
 *
 * @since 0.1.0
 */
public class ServerPushDemo {

    private final Http3Server server;

    /**
     * Creates a new server push demo with a page and stylesheet endpoint.
     *
     * @since 0.1.0
     */
    public ServerPushDemo() {
        var config = Http3Config.defaults().enablePush(true);
        this.server = new Http3Server(config);

        server.router().get("/page", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "<html><link rel='stylesheet' href='/style.css'></html>"));
        server.router().get("/style.css", (ctx, req) -> {
            var response = HttpResponse.of(HttpStatus.OK, "body { color: black; }");
            response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css");
            return response;
        });
    }

    /**
     * Returns the server.
     *
     * @return the server
     * @since 0.1.0
     */
    public Http3Server server() {
        return server;
    }

    /**
     * Handles a request and pushes the stylesheet alongside the page response.
     *
     * @param connection   the HTTP/3 connection
     * @param requestStream the request stream
     * @param headers      the request headers
     * @param body         the request body
     * @since 0.1.0
     */
    public void handleRequestWithPush(Http3Connection connection, QuicStream requestStream,
                                      List<Map.Entry<String, String>> headers, ByteBuffer body) {
        // Handle the main request
        server.handleRequest(connection, requestStream, headers, body);

        // Push the stylesheet
        var pushRequest = HttpRequest.of(HttpMethod.GET, "/style.css");
        pushRequest.getHeaders().set(HttpHeaders.HOST, "localhost");
        var pushResponse = HttpResponse.of(HttpStatus.OK, "body { color: black; }");
        pushResponse.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css");

        server.handlePushPromise(connection, requestStream, 0, pushRequest, pushResponse);
    }
}
