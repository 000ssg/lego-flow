package ssg.legoflow.http3.server;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.server.HttpRouter;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.Http3Settings;
import ssg.legoflow.http3.config.Http3Config;
import ssg.legoflow.http3.quic.QuicConnection;
import ssg.legoflow.http3.quic.QuicStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * HTTP/3 server built on QUIC transport.
 *
 * <p>Creates {@link Http3Connection} instances for incoming QUIC connections,
 * listens for request streams, adapts requests via {@link Http3RequestAdapter},
 * and routes them through the standard {@link HttpRouter}. Supports server
 * push via PUSH_PROMISE frames.</p>
 *
 * <p>This class is thread-safe.</p>
 *
 * @since 0.1.0
 */
public class Http3Server {

    private static final Logger LOG = LoggerFactory.getLogger(Http3Server.class);

    private final Http3Config config;
    private final HttpRouter router;
    private final Http3RequestAdapter requestAdapter;
    private final List<Http3Connection> connections;

    /**
     * Creates a new HTTP/3 server with the given configuration.
     *
     * @param config the server configuration
     * @since 0.1.0
     */
    public Http3Server(Http3Config config) {
        this.config = config;
        this.router = new HttpRouter();
        this.requestAdapter = new Http3RequestAdapter();
        this.connections = new CopyOnWriteArrayList<>();
    }

    /**
     * Creates a new HTTP/3 server with the given router and configuration.
     *
     * @param router the HTTP router
     * @param config the server configuration
     * @since 0.1.0
     */
    public Http3Server(HttpRouter router, Http3Config config) {
        this.config = config;
        this.router = router;
        this.requestAdapter = new Http3RequestAdapter();
        this.connections = new CopyOnWriteArrayList<>();
    }

    /**
     * Returns the HTTP router.
     *
     * @return the router
     * @since 0.1.0
     */
    public HttpRouter router() {
        return router;
    }

    /**
     * Returns the server configuration.
     *
     * @return the configuration
     * @since 0.1.0
     */
    public Http3Config config() {
        return config;
    }

    /**
     * Accepts a new QUIC connection and wraps it as an HTTP/3 connection.
     *
     * <p>Creates the HTTP/3 control and QPACK streams, sends initial
     * SETTINGS, and adds the connection to the active connections list.</p>
     *
     * @param quicConnection the incoming QUIC connection
     * @return the established HTTP/3 connection
     * @since 0.1.0
     */
    public Http3Connection acceptConnection(QuicConnection quicConnection) {
        var settings = Http3Settings.builder()
                .maxFieldSectionSize(config.maxFieldSectionSize())
                .qpackMaxTableCapacity(config.qpackMaxTableCapacity())
                .qpackBlockedStreams(config.qpackBlockedStreams())
                .build();

        var connection = new Http3Connection(quicConnection, settings);
        connection.initialize();
        connections.add(connection);
        LOG.info("HTTP/3 connection accepted from QUIC connection {}", quicConnection.sourceConnectionId());
        return connection;
    }

    /**
     * Handles an incoming request on the given stream.
     *
     * <p>Decodes the request headers and body, routes the request through
     * the router, and sends the response back on the same stream.</p>
     *
     * @param connection the HTTP/3 connection
     * @param stream     the request stream
     * @param headers    the decoded request headers
     * @param body       the request body, or {@code null}
     * @since 0.1.0
     */
    public void handleRequest(Http3Connection connection, QuicStream stream,
                              List<Map.Entry<String, String>> headers, ByteBuffer body) {
        var request = requestAdapter.adaptRequest(headers, body);
        var response = dispatchRequest(request);

        var responseHeaders = requestAdapter.adaptResponseHeaders(response);
        var responseBody = requestAdapter.adaptResponseBody(response);
        connection.sendResponse(stream, responseHeaders, responseBody);
    }

    /**
     * Handles a server push: sends a PUSH_PROMISE followed by the pushed response.
     *
     * @param connection      the HTTP/3 connection
     * @param parentStream    the parent request stream
     * @param pushId          the push ID
     * @param promisedRequest the promised request
     * @param promisedResponse the response to push
     * @since 0.1.0
     */
    public void handlePushPromise(Http3Connection connection, QuicStream parentStream,
                                  long pushId, HttpRequest promisedRequest, HttpResponse promisedResponse) {
        // Build promise headers
        var promiseHeaders = new ArrayList<Map.Entry<String, String>>();
        promiseHeaders.add(new AbstractMap.SimpleEntry<>(":method", promisedRequest.getMethod().name()));
        promiseHeaders.add(new AbstractMap.SimpleEntry<>(":path", promisedRequest.getUri()));
        promiseHeaders.add(new AbstractMap.SimpleEntry<>(":scheme", "https"));
        var host = promisedRequest.getHeaders().get(HttpHeaders.HOST);
        if (host != null) {
            promiseHeaders.add(new AbstractMap.SimpleEntry<>(":authority", host));
        }

        // Send PUSH_PROMISE on parent stream
        connection.sendPushPromise(parentStream, pushId, promiseHeaders);

        // Send the pushed response on a new stream
        var pushStream = connection.quicConnection().createStream(true);
        var responseHeaders = requestAdapter.adaptResponseHeaders(promisedResponse);
        var responseBody = requestAdapter.adaptResponseBody(promisedResponse);
        connection.sendResponse(pushStream, responseHeaders, responseBody);
    }

    /**
     * Starts the server (placeholder for real transport binding).
     *
     * @since 0.1.0
     */
    public void start() {
        LOG.info("HTTP/3 server started on {}:{}", config.host(), config.port());
    }

    /**
     * Stops the server and closes all connections.
     *
     * @since 0.1.0
     */
    public void stop() {
        for (var connection : connections) {
            connection.close();
        }
        connections.clear();
        LOG.info("HTTP/3 server stopped");
    }

    /**
     * Returns the list of active connections.
     *
     * @return an unmodifiable list of active connections
     * @since 0.1.0
     */
    public List<Http3Connection> getActiveConnections() {
        return List.copyOf(connections);
    }

    /**
     * Removes a connection from the active list.
     *
     * @param connection the connection to remove
     * @since 0.1.0
     */
    public void removeConnection(Http3Connection connection) {
        connections.remove(connection);
    }

    private HttpResponse dispatchRequest(HttpRequest request) {
        try {
            var ctx = new SimpleHttpContext(request);
            return router.dispatch(ctx, request);
        } catch (Exception e) {
            return HttpResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
        }
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
            return LoggerFactory.getLogger(Http3Server.class);
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
