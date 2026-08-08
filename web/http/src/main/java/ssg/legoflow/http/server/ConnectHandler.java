package ssg.legoflow.http.server;

import ssg.legoflow.http.core.*;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP CONNECT method handler per RFC 7231 §4.3.6.
 *
 * <p>The CONNECT method requests that the proxy establish a tunnel to the
 * target host. The request-target is the authority (host:port) of the
 * destination server.
 *
 * @since 0.1.0
 */
public class ConnectHandler implements HttpRequestHandler {

    private final Set<String> allowedHosts;
    private final TunnelCallback tunnelCallback;

    /**
     * Callback interface invoked when a tunnel is established.
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    public interface TunnelCallback {
        /**
         * Called to relay data bidirectionally between client and target.
         *
         * @param targetHost the target hostname
         * @param targetPort the target port
         * @param clientData any data from the client after the CONNECT request
         */
        void onTunnelEstablished(String targetHost, int targetPort, ByteBuffer clientData);
    }

    /**
     * Creates a CONNECT handler that allows connections to any host.
     *
     * @param tunnelCallback callback for tunnel establishment
     */
    public ConnectHandler(TunnelCallback tunnelCallback) {
        this(null, tunnelCallback);
    }

    /**
     * Creates a CONNECT handler that only allows connections to specified hosts.
     *
     * @param allowedHosts   set of allowed host:port patterns, or null for any
     * @param tunnelCallback callback for tunnel establishment
     */
    public ConnectHandler(Set<String> allowedHosts, TunnelCallback tunnelCallback) {
        this.allowedHosts = allowedHosts;
        this.tunnelCallback = Objects.requireNonNull(tunnelCallback, "tunnelCallback must not be null");
    }

    @Override
    public HttpResponse handle(HttpContext ctx, HttpRequest request) {
        if (request.getMethod() != HttpMethod.CONNECT) {
            return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
        }

        String authority = request.getUri();
        HostPort hostPort = parseAuthority(authority);
        if (hostPort == null) {
            return HttpResponse.of(HttpStatus.BAD_REQUEST, "Invalid authority: " + authority);
        }

        if (allowedHosts != null && !allowedHosts.contains(authority)) {
            return HttpResponse.of(HttpStatus.FORBIDDEN, "CONNECT to " + authority + " not allowed");
        }

        // Signal tunnel establishment
        tunnelCallback.onTunnelEstablished(hostPort.host(), hostPort.port(), request.getBody());

        // Return 200 Connection Established
        return HttpResponse.of(HttpStatus.OK, "");
    }

    /**
     * Parses an authority string (host:port) from the CONNECT request target.
     *
     * @param authority the authority string
     * @return the parsed host and port, or null if invalid
     */
    public HostPort parseAuthority(String authority) {
        if (authority == null || authority.isBlank()) {
            return null;
        }
        int colonIndex = authority.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == authority.length() - 1) {
            return null;
        }
        String host = authority.substring(0, colonIndex);
        try {
            int port = Integer.parseInt(authority.substring(colonIndex + 1));
            if (port < 1 || port > 65535) {
                return null;
            }
            return new HostPort(host, port);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Represents a parsed host and port pair.
     *
     * @param host the hostname
     * @param port the port number
     * @since 0.1.0
     */
    public record HostPort(String host, int port) {
        public HostPort {
            Objects.requireNonNull(host, "host must not be null");
        }

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }
}
