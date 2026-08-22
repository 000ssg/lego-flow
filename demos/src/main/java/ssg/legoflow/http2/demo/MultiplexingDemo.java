package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.client.Http2Client;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.frame.Http2Frame;
import ssg.legoflow.http2.server.Http2Server;
import java.util.ArrayList;
import java.util.List;
public class MultiplexingDemo {

    private final Http2Server server;
    private final Http2Client client;

    public MultiplexingDemo() {
        var config = Http2Config.defaults().maxConcurrentStreams(10);
        this.server = new Http2Server(config);
        this.client = new Http2Client(config);

        server.router().get("/resource/1", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 1"));
        server.router().get("/resource/2", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 2"));
        server.router().get("/resource/3", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Resource 3"));
    }

    public Http2Server server() {
        return server;
    }

    public Http2Client client() {
        return client;
    }

    public List<Http2Frame> sendConcurrentRequests(String... paths) {
        var allFrames = new ArrayList<Http2Frame>();
        for (String path : paths) {
            var request = HttpRequest.of(HttpMethod.GET, path);
            request.getHeaders().set(HttpHeaders.HOST, "localhost");
            allFrames.addAll(client.sendRequest(request));
        }
        return allFrames;
    }
}
