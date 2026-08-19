package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.frame.Http2Frame;
import ssg.legoflow.http2.server.Http2Server;
import ssg.legoflow.http2.stream.Http2Stream;
import java.util.ArrayList;
import java.util.List;
public class ServerPushDemo {

    private final Http2Server server;

    public ServerPushDemo() {
        var config = Http2Config.defaults().enablePush(true);
        this.server = new Http2Server(config);

        server.router().get("/page", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "<html><link rel='stylesheet' href='/style.css'></html>"));
        server.router().get("/style.css", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "body { color: black; }"));
    }

    public Http2Server server() {
        return server;
    }

    public List<Http2Frame> handleRequestWithPush(Http2Connection connection, Http2Stream requestStream) {
        var outFrames = new ArrayList<Http2Frame>();

        outFrames.addAll(server.handleRequest(connection, requestStream));

        var pushRequest = HttpRequest.of(HttpMethod.GET, "/style.css");
        pushRequest.getHeaders().set(HttpHeaders.HOST, "localhost");
        var pushResponse = HttpResponse.of(HttpStatus.OK, "body { color: black; }");
        pushResponse.getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/css");

        outFrames.addAll(server.handlePushPromise(connection, requestStream.streamId(),
                pushRequest, pushResponse));

        return outFrames;
    }
}
