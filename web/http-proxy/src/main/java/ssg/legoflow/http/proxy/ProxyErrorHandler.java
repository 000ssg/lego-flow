package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;
import ssg.legoflow.http.core.HttpVersion;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
/**
 * Error handling for proxy operations.
 *
 * <p>Produces appropriate HTTP error responses for common proxy failure scenarios:
 * 502 Bad Gateway, 504 Gateway Timeout, 503 Service Unavailable.</p>
 *
 * @since 0.1.0
 */
public class ProxyErrorHandler {

    private final String proxyName;

    /**
     * Creates a new proxy error handler.
     *
     * @param proxyName the proxy name for error messages
     * @since 0.1.0
     */
    public ProxyErrorHandler(String proxyName) {
        this.proxyName = proxyName;
    }

    /**
     * Creates an error response for the given exception.
     *
     * @param cause the exception that occurred
     * @return an appropriate HTTP error response
     * @since 0.1.0
     */
    public HttpResponse handleError(Throwable cause) {
        if (cause instanceof SocketTimeoutException) {
            return gatewayTimeout(cause.getMessage());
        } else if (cause instanceof ConnectException) {
            return badGateway("Connection refused: " + cause.getMessage());
        } else {
            return badGateway(cause.getMessage());
        }
    }

    /**
     * Creates a 502 Bad Gateway response.
     *
     * @param detail the error detail message
     * @return the error response
     * @since 0.1.0
     */
    public HttpResponse badGateway(String detail) {
        return errorResponse(HttpStatus.BAD_GATEWAY, detail);
    }

    /**
     * Creates a 504 Gateway Timeout response.
     *
     * @param detail the error detail message
     * @return the error response
     * @since 0.1.0
     */
    public HttpResponse gatewayTimeout(String detail) {
        return errorResponse(HttpStatus.GATEWAY_TIMEOUT, detail);
    }

    /**
     * Creates a 503 Service Unavailable response.
     *
     * @param detail the error detail message
     * @return the error response
     * @since 0.1.0
     */
    public HttpResponse serviceUnavailable(String detail) {
        return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, detail);
    }

    /**
     * Creates an error response with the specified status.
     *
     * @param status the HTTP status
     * @param detail the error detail
     * @return the error response
     * @since 0.1.0
     */
    public HttpResponse errorResponse(HttpStatus status, String detail) {
        String body = status.code() + " " + status.reason() + "\n"
                + "Proxy: " + proxyName + "\n"
                + (detail != null ? "Detail: " + detail + "\n" : "");
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_TYPE, "text/plain; charset=utf-8");
        headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(bodyBytes.length));
        headers.set(HttpHeaders.CONNECTION, "close");
        HttpResponse response = new HttpResponse(status, HttpVersion.HTTP_1_1, headers);
        response.setBody(ByteBuffer.wrap(bodyBytes));
        return response;
    }

    /**
     * Returns the proxy name used in error messages.
     *
     * @return the proxy name
     * @since 0.1.0
     */
    public String getProxyName() {
        return proxyName;
    }
}
