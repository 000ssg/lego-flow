package ssg.legoflow.http3.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.client.Http3Client;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicStream;
import ssg.legoflow.http3.server.Http3Server;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demonstrates concurrent requests over multiplexed QUIC streams.
 *
 * <p>Creates a server with multiple endpoints and sends multiple
 * requests concurrently using separate QUIC bidirectional streams.</p>
 *
 * @since 0.1.0
 */
public class MultiplexingDemo {

    private final Http3Server server;
    private final Http3Config config;

    /**
     * Creates a new multiplexing demo with three resource endpoints.
     *
     * @since 0.1.0
     */
    public MultiplexingDemo() {
        this.config = Http3Config.defaults().maxConcurrentStreams(10);
        this.server = new Http3Server(config);

        server.router().get("/resource/1", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 1"));
        server.router().get("/resource/2", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 2"));
        server.router().get("/resource/3", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 3"));
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
     * Returns the config.
     *
     * @return the config
     * @since 0.1.0
     */
    public Http3Config config() {
        return config;
    }

    /**
     * Sends concurrent requests on separate streams.
     *
     * @param connection the HTTP/3 connection
     * @param paths      the request paths
     * @return the list of streams used for the requests
     * @since 0.1.0
     */
    public List<QuicStream> sendConcurrentRequests(Http3Connection connection, String... paths) {
        var streams = new ArrayList<QuicStream>();
        for (String path : paths) {
            var headers = List.<Map.Entry<String, String>>of(
                    new AbstractMap.SimpleEntry<>(":method", "GET"),
                    new AbstractMap.SimpleEntry<>(":path", path),
                    new AbstractMap.SimpleEntry<>(":scheme", "https"),
                    new AbstractMap.SimpleEntry<>(":authority", "localhost")
            );
            var stream = connection.sendRequest(headers, null);
            streams.add(stream);
        }
        return streams;
    }
}
