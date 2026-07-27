package ssg.legoflow.http2.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.http2.config.Http2Config;
import ssg.legoflow.http2.connection.Http2Connection;
import ssg.legoflow.http2.connection.Http2Settings;
import ssg.legoflow.http2.frame.*;
import ssg.legoflow.http2.hpack.HpackEncoder;
import ssg.legoflow.http2.stream.Http2Stream;
import ssg.legoflow.http2.stream.Http2StreamState;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class Http2Server {

    private final Http2Config config;
    private final HttpRouter router;
    private final Http2RequestAdapter requestAdapter;
    private final List<Http2Connection> connections;

    public Http2Server(Http2Config config) {
        this.config = config;
        this.router = new HttpRouter();
        this.requestAdapter = new Http2RequestAdapter();
        this.connections = new CopyOnWriteArrayList<>();
    }

    public Http2Server(HttpRouter router, Http2Config config) {
        this.config = config;
        this.router = router;
        this.requestAdapter = new Http2RequestAdapter();
        this.connections = new CopyOnWriteArrayList<>();
    }

    public HttpRouter router() {
        return router;
    }

    public Http2Config config() {
        return config;
    }

    public Http2Connection acceptConnection() {
        var settings = new Http2Settings();
        settings.set(Http2Settings.MAX_CONCURRENT_STREAMS, config.maxConcurrentStreams());
        settings.set(Http2Settings.INITIAL_WINDOW_SIZE, config.initialWindowSize());
        settings.set(Http2Settings.MAX_FRAME_SIZE, config.maxFrameSize());
        settings.set(Http2Settings.MAX_HEADER_LIST_SIZE, config.maxHeaderListSize());
        settings.set(Http2Settings.HEADER_TABLE_SIZE, config.headerTableSize());
        if (!config.enablePush()) {
            settings.set(Http2Settings.ENABLE_PUSH, 0);
        }

        var connection = new Http2Connection(true, settings);
        connections.add(connection);
        return connection;
    }

    public List<Http2Frame> handleRequest(Http2Connection connection, Http2Stream stream) {
        var outFrames = new ArrayList<Http2Frame>();

        var request = requestAdapter.adapt(stream);
        var response = dispatchRequest(request);
        var responseHeaders = requestAdapter.adaptResponseHeaders(response);

        var encodedHeaders = connection.encoder().encode(responseHeaders);
        boolean hasBody = response.getBody() != null && response.getBody().hasRemaining();
        outFrames.add(Http2Frame.headers(stream.streamId(), encodedHeaders, !hasBody, true));

        if (hasBody) {
            var body = response.getBody().duplicate();
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

        if (stream.state() == Http2StreamState.HALF_CLOSED_REMOTE) {
            stream.transitionTo(Http2StreamState.CLOSED);
        }

        return outFrames;
    }

    public List<Http2Frame> handlePushPromise(Http2Connection connection, int parentStreamId,
                                                HttpRequest promisedRequest, HttpResponse promisedResponse) {
        var outFrames = new ArrayList<Http2Frame>();

        var promisedStream = connection.streamManager().createStream();
        promisedStream.transitionTo(Http2StreamState.RESERVED_LOCAL);

        var promiseHeaders = new HttpHeaders();
        promiseHeaders.set(":method", promisedRequest.getMethod().name());
        promiseHeaders.set(":path", promisedRequest.getUri());
        promiseHeaders.set(":scheme", "https");
        if (promisedRequest.getHeaders().contains(HttpHeaders.HOST)) {
            promiseHeaders.set(":authority", promisedRequest.getHeaders().get(HttpHeaders.HOST));
        }

        var encodedPromiseHeaders = connection.encoder().encode(promiseHeaders);
        outFrames.add(Http2Frame.pushPromise(parentStreamId, promisedStream.streamId(), encodedPromiseHeaders));

        promisedStream.transitionTo(Http2StreamState.HALF_CLOSED_REMOTE);

        var responseHeaders = requestAdapter.adaptResponseHeaders(promisedResponse);
        var encodedResponseHeaders = connection.encoder().encode(responseHeaders);
        boolean hasBody = promisedResponse.getBody() != null && promisedResponse.getBody().hasRemaining();
        outFrames.add(Http2Frame.headers(promisedStream.streamId(), encodedResponseHeaders, !hasBody, true));

        if (hasBody) {
            var body = promisedResponse.getBody().duplicate();
            outFrames.add(Http2Frame.data(promisedStream.streamId(), body, true));
        }

        promisedStream.transitionTo(Http2StreamState.CLOSED);

        return outFrames;
    }

    private HttpResponse dispatchRequest(HttpRequest request) {
        try {
            var ctx = new SimpleHttpContext(request);
            return router.dispatch(ctx, request);
        } catch (Exception e) {
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
    }

    public List<Http2Connection> connections() {
        return List.copyOf(connections);
    }

    public void removeConnection(Http2Connection connection) {
        connections.remove(connection);
    }

    private static class SimpleHttpContext implements HttpContext {
        private final HttpRequest request;
        private HttpResponse response;

        SimpleHttpContext(HttpRequest request) {
            this.request = request;
        }

        @Override public HttpRequest getRequest() { return request; }
        @Override public HttpResponse getResponse() { return response; }
        @Override public void setResponse(HttpResponse response) { this.response = response; }
        @Override public org.slf4j.Logger getLogger() {
            return org.slf4j.LoggerFactory.getLogger(Http2Server.class);
        }
        @Override public ssg.legoflow.blocks.ProcessorStatistics getStatistics() {
            return new ssg.legoflow.blocks.ProcessorStatistics();
        }
        @Override public void handleError(Throwable error) {
            getLogger().error("Error: {}", error.getMessage(), error);
        }
        @Override public <T> T getAttribute(String key) { return null; }
        @Override public void setAttribute(String key, Object value) {}
        @Override public ssg.legoflow.service.scope.SiteScope getSiteScope() { return null; }
        @Override public ssg.legoflow.service.scope.ApplicationScope getApplicationScope() { return null; }
        @Override public ssg.legoflow.service.scope.SessionScope getSessionScope() { return null; }
        @Override public ssg.legoflow.service.scope.RequestScope getRequestScope() { return null; }
        @Override public ssg.legoflow.service.user.ServiceUser getUser() {
            return ssg.legoflow.service.user.ServiceUser.anonymous();
        }
        @Override public boolean hasRole(ssg.legoflow.service.user.ServiceRole role) { return false; }
        @Override public void checkPermission(String operation) {}
    }
}
