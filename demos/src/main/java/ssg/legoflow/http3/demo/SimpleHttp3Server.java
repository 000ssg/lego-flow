package ssg.legoflow.http3.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicStream;
import ssg.legoflow.http3.server.Http3Server;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
/**
 * Simplest HTTP/3 server demo: a single endpoint returning plain text over QUIC.
 *
 * @since 0.1.0
 */
public class SimpleHttp3Server {

    private final Http3Server server;

    /**
     * Creates a new simple HTTP/3 server with a {@code /hello} endpoint.
     *
     * @since 0.1.0
     */
    public SimpleHttp3Server() {
        this.server = new Http3Server(Http3Config.defaults());
        server.router().get("/hello", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello, HTTP/3!"));
    }

    /**
     * Returns the underlying HTTP/3 server.
     *
     * @return the server
     * @since 0.1.0
     */
    public Http3Server server() {
        return server;
    }

    /**
     * Accepts a QUIC connection and returns the HTTP/3 connection.
     *
     * @param quicConnection the incoming QUIC connection
     * @return the HTTP/3 connection
     * @since 0.1.0
     */
    public Http3Connection acceptConnection(QuicConnection quicConnection) {
        return server.acceptConnection(quicConnection);
    }

    /**
     * Handles a request with the given headers and body on the specified stream.
     *
     * @param connection the HTTP/3 connection
     * @param stream     the request stream
     * @param headers    the request headers
     * @param body       the request body
     * @since 0.1.0
     */
    public void handleRequest(Http3Connection connection, QuicStream stream,
                              List<Map.Entry<String, String>> headers, ByteBuffer body) {
        server.handleRequest(connection, stream, headers, body);
    }
}
