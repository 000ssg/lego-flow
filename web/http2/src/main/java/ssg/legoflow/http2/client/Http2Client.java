package ssg.legoflow.http2.client;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2Settings;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.stream.Http2Stream;
import ssg.legoflow.http2.stream.Http2StreamState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class Http2Client {

    private final Http2Config config;
    private Http2Connection connection;
    private final Map<Integer, CompletableFuture<HttpResponse>> pendingRequests = new ConcurrentHashMap<>();

    public Http2Client(Http2Config config) {
        this.config = config;
    }

    public Http2Connection connect() {
        var settings = new Http2Settings();
        settings.set(Http2Settings.MAX_CONCURRENT_STREAMS, config.maxConcurrentStreams());
        settings.set(Http2Settings.INITIAL_WINDOW_SIZE, config.initialWindowSize());
        settings.set(Http2Settings.MAX_FRAME_SIZE, config.maxFrameSize());
        settings.set(Http2Settings.HEADER_TABLE_SIZE, config.headerTableSize());
        settings.set(Http2Settings.ENABLE_PUSH, config.enablePush() ? 1 : 0);

        connection = new Http2Connection(false, settings);
        return connection;
    }

    public List<Http2Frame> sendRequest(HttpRequest request) {
        if (connection == null) {
            throw new IllegalStateException("Not connected");
        }

        var outFrames = new ArrayList<Http2Frame>();
        var stream = connection.streamManager().createStream();
        stream.transitionTo(Http2StreamState.OPEN);

        var h2Headers = new HttpHeaders();
        h2Headers.set(":method", request.getMethod().name());
        h2Headers.set(":path", request.getUri());
        h2Headers.set(":scheme", "https");
        var host = request.getHeaders().get(HttpHeaders.HOST);
        if (host != null) {
            h2Headers.set(":authority", host);
        }

        for (String name : request.getHeaders().names()) {
            String lower = name.toLowerCase();
            if (!lower.equals("host") && !lower.equals("connection")
                    && !lower.equals("transfer-encoding")) {
                for (String value : request.getHeaders().getAll(name)) {
                    h2Headers.add(name, value);
                }
            }
        }

        var encoded = connection.encoder().encode(h2Headers);
        boolean hasBody = request.getBody() != null && request.getBody().hasRemaining();
        outFrames.add(Http2Frame.headers(stream.streamId(), encoded, !hasBody, true));

        if (hasBody) {
            var body = request.getBody().duplicate();
            int maxFrameSize = connection.localSettings().maxFrameSize();
            while (body.hasRemaining()) {
                int chunkSize = Math.min(body.remaining(), maxFrameSize);
                var chunk = ByteBuffer.allocate(chunkSize);
                for (int i = 0; i < chunkSize; i++) {
                    chunk.put(body.get());
                }
                chunk.flip();
                boolean endStream = !body.hasRemaining();
                outFrames.add(Http2Frame.data(stream.streamId(), chunk, endStream));
            }
        }

        if (!hasBody) {
            stream.transitionTo(Http2StreamState.HALF_CLOSED_LOCAL);
        }

        return outFrames;
    }

    public HttpResponse buildResponse(Http2Stream stream) {
        var h2Headers = stream.headers();
        String statusStr = h2Headers.get(":status");
        if (statusStr == null) {
            throw new IllegalStateException("Missing :status pseudo-header in response");
        }

        int statusCode = Integer.parseInt(statusStr);
        HttpStatus status = HttpStatus.fromCode(statusCode);

        var headers = new HttpHeaders();
        for (String name : h2Headers.names()) {
            if (!name.startsWith(":")) {
                for (String value : h2Headers.getAll(name)) {
                    headers.add(name, value);
                }
            }
        }

        var response = new HttpResponse(status, HttpVersion.HTTP_2, headers);
        var data = stream.getAccumulatedData();
        if (data.hasRemaining()) {
            response.setBody(data);
        }

        return response;
    }

    public Http2Connection connection() {
        return connection;
    }

    public Http2Config config() {
        return config;
    }
}
