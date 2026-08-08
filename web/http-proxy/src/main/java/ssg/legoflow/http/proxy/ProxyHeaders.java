package ssg.legoflow.http.proxy;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;

/**
 * Standard proxy headers: X-Forwarded-For, X-Forwarded-Proto, X-Forwarded-Host, Via, X-Real-IP.
 *
 * <p>Provides utility methods for reading and writing proxy-related headers
 * per RFC 7230 section 5.7 (Message Forwarding) and the de facto X-Forwarded-* convention.</p>
 *
 * @since 0.1.0
 */
public final class ProxyHeaders {

    /** The X-Forwarded-For header name. */
    public static final String X_FORWARDED_FOR = "x-forwarded-for";

    /** The X-Forwarded-Proto header name. */
    public static final String X_FORWARDED_PROTO = "x-forwarded-proto";

    /** The X-Forwarded-Host header name. */
    public static final String X_FORWARDED_HOST = "x-forwarded-host";

    /** The Via header name (RFC 7230 section 5.7.1). */
    public static final String VIA = "via";

    /** The X-Real-IP header name. */
    public static final String X_REAL_IP = "x-real-ip";

    /** The Proxy-Authorization header name (RFC 7235 section 4.4). */
    public static final String PROXY_AUTHORIZATION = "proxy-authorization";

    /** The Proxy-Authenticate header name (RFC 7235 section 4.3). */
    public static final String PROXY_AUTHENTICATE = "proxy-authenticate";

    private ProxyHeaders() {
        // utility class
    }

    /**
     * Appends a client IP to the X-Forwarded-For header of a request.
     *
     * @param headers the HTTP headers to modify
     * @param clientIp the client IP address
     * @since 0.1.0
     */
    public static void addForwardedFor(HttpHeaders headers, String clientIp) {
        String existing = headers.get(X_FORWARDED_FOR);
        if (existing != null && !existing.isEmpty()) {
            headers.set(X_FORWARDED_FOR, existing + ", " + clientIp);
        } else {
            headers.set(X_FORWARDED_FOR, clientIp);
        }
    }

    /**
     * Returns the X-Forwarded-For header value, or null if absent.
     *
     * @param headers the HTTP headers
     * @return the forwarded-for chain, or null
     * @since 0.1.0
     */
    public static String getForwardedFor(HttpHeaders headers) {
        return headers.get(X_FORWARDED_FOR);
    }

    /**
     * Sets the X-Forwarded-Proto header.
     *
     * @param headers the HTTP headers to modify
     * @param protocol the protocol (e.g. "http" or "https")
     * @since 0.1.0
     */
    public static void setForwardedProto(HttpHeaders headers, String protocol) {
        headers.set(X_FORWARDED_PROTO, protocol);
    }

    /**
     * Returns the X-Forwarded-Proto header value, or null if absent.
     *
     * @param headers the HTTP headers
     * @return the protocol, or null
     * @since 0.1.0
     */
    public static String getForwardedProto(HttpHeaders headers) {
        return headers.get(X_FORWARDED_PROTO);
    }

    /**
     * Sets the X-Forwarded-Host header.
     *
     * @param headers the HTTP headers to modify
     * @param host the original host
     * @since 0.1.0
     */
    public static void setForwardedHost(HttpHeaders headers, String host) {
        headers.set(X_FORWARDED_HOST, host);
    }

    /**
     * Returns the X-Forwarded-Host header value, or null if absent.
     *
     * @param headers the HTTP headers
     * @return the original host, or null
     * @since 0.1.0
     */
    public static String getForwardedHost(HttpHeaders headers) {
        return headers.get(X_FORWARDED_HOST);
    }

    /**
     * Adds a Via header entry per RFC 7230 section 5.7.1.
     *
     * @param headers the HTTP headers to modify
     * @param protocolVersion the protocol version (e.g. "1.1")
     * @param pseudonym the proxy pseudonym or host
     * @since 0.1.0
     */
    public static void addVia(HttpHeaders headers, String protocolVersion, String pseudonym) {
        String viaEntry = protocolVersion + " " + pseudonym;
        String existing = headers.get(VIA);
        if (existing != null && !existing.isEmpty()) {
            headers.set(VIA, existing + ", " + viaEntry);
        } else {
            headers.set(VIA, viaEntry);
        }
    }

    /**
     * Returns the Via header value, or null if absent.
     *
     * @param headers the HTTP headers
     * @return the Via chain, or null
     * @since 0.1.0
     */
    public static String getVia(HttpHeaders headers) {
        return headers.get(VIA);
    }

    /**
     * Sets the X-Real-IP header.
     *
     * @param headers the HTTP headers to modify
     * @param ip the real client IP
     * @since 0.1.0
     */
    public static void setRealIp(HttpHeaders headers, String ip) {
        headers.set(X_REAL_IP, ip);
    }

    /**
     * Returns the X-Real-IP header value, or null if absent.
     *
     * @param headers the HTTP headers
     * @return the real IP, or null
     * @since 0.1.0
     */
    public static String getRealIp(HttpHeaders headers) {
        return headers.get(X_REAL_IP);
    }

    /**
     * Applies standard proxy headers to a forwarded request.
     * Sets X-Forwarded-For, X-Forwarded-Proto, X-Forwarded-Host, Via, and X-Real-IP.
     *
     * @param headers the HTTP headers to modify
     * @param clientIp the client IP address
     * @param protocol the protocol (e.g. "http")
     * @param originalHost the original Host header value
     * @param proxyName the name of this proxy for the Via header
     * @since 0.1.0
     */
    public static void applyForwardHeaders(HttpHeaders headers, String clientIp,
                                           String protocol, String originalHost, String proxyName) {
        addForwardedFor(headers, clientIp);
        setForwardedProto(headers, protocol);
        if (originalHost != null) {
            setForwardedHost(headers, originalHost);
        }
        addVia(headers, "1.1", proxyName);
        setRealIp(headers, clientIp);
    }
}
