package ssg.legoflow.http2.demo;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2ConnectionPreface;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.server.Http2Server;
import ssg.legoflow.http2.stream.Http2Stream;
import ssg.legoflow.http2.stream.Http2StreamState;

import java.util.ArrayList;
import java.util.List;

public class SimpleHttp2Server {

    private final Http2Server server;

    public SimpleHttp2Server() {
        this.server = new Http2Server(Http2Config.defaults());
        server.router().get("/hello", (ctx, req) ->
                HttpResponse.of(HttpStatus.OK, "Hello, HTTP/2!"));
    }

    public Http2Server server() {
        return server;
    }

    public List<Http2Frame> handleClientPreface(Http2Connection connection, java.nio.ByteBuffer preface) {
        return connection.handlePreface(preface);
    }

    public List<Http2Frame> processIncomingFrames(Http2Connection connection, List<Http2Frame> frames) {
        var outFrames = new ArrayList<Http2Frame>();
        for (var frame : frames) {
            outFrames.addAll(connection.processFrame(frame));

            if (frame.type() == Http2FrameType.HEADERS && frame.hasFlag(Http2Flags.END_HEADERS)) {
                var stream = connection.streamManager().getStream(frame.streamId());
                if (stream != null && isRequestComplete(frame, stream)) {
                    outFrames.addAll(server.handleRequest(connection, stream));
                }
            }
        }
        return outFrames;
    }

    private boolean isRequestComplete(Http2Frame frame, Http2Stream stream) {
        return frame.hasFlag(Http2Flags.END_STREAM)
            || stream.state() == Http2StreamState.HALF_CLOSED_REMOTE;
    }
}
