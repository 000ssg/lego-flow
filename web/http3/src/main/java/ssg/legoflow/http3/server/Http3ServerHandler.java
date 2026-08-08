package ssg.legoflow.http3.server;

import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http3.Http3Connection;
import ssg.legoflow.http3.quic.QuicStream;

/**
 * Functional interface for handling HTTP/3 requests.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface Http3ServerHandler {

    /**
     * Handles an HTTP/3 request on the given connection and stream.
     *
     * @param connection the HTTP/3 connection
     * @param stream     the QUIC stream carrying the request
     * @param request    the adapted HTTP request
     * @since 0.1.0
     */
    void handle(Http3Connection connection, QuicStream stream, HttpRequest request);
}
