package ssg.legoflow.http.server;

import ssg.legoflow.http.core.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * HTTP request router with automatic OPTIONS and TRACE method handling.
 *
 * @since 1.0.0
 */
public class HttpRouter {

    private final Map<String, Map<HttpMethod, HttpRequestHandler>> routes = new ConcurrentHashMap<>();
    private HttpRequestHandler defaultHandler = (ctx, req) ->
            HttpResponse.of(HttpStatus.NOT_FOUND, "Not Found");
    private boolean traceEnabled = true;

    public HttpRouter route(String path, HttpMethod method, HttpRequestHandler handler) {
        routes.computeIfAbsent(path, _ -> new ConcurrentHashMap<>()).put(method, handler);
        return this;
    }

    public HttpRouter get(String path, HttpRequestHandler handler) {
        return route(path, HttpMethod.GET, handler);
    }

    public HttpRouter post(String path, HttpRequestHandler handler) {
        return route(path, HttpMethod.POST, handler);
    }

    public HttpRouter put(String path, HttpRequestHandler handler) {
        return route(path, HttpMethod.PUT, handler);
    }

    public HttpRouter delete(String path, HttpRequestHandler handler) {
        return route(path, HttpMethod.DELETE, handler);
    }

    public HttpRouter setDefaultHandler(HttpRequestHandler handler) {
        this.defaultHandler = handler;
        return this;
    }

    /**
     * Enables or disables TRACE method handling.
     *
     * <p>TRACE can be disabled for security reasons to prevent
     * cross-site tracing (XST) attacks.
     *
     * @param enabled true to enable TRACE, false to disable
     * @return this router for chaining
     */
    public HttpRouter setTraceEnabled(boolean enabled) {
        this.traceEnabled = enabled;
        return this;
    }

    /**
     * Returns whether TRACE method handling is enabled.
     *
     * @return true if TRACE is enabled
     */
    public boolean isTraceEnabled() {
        return traceEnabled;
    }

    public HttpResponse dispatch(HttpContext ctx, HttpRequest request) {
        // Handle TRACE method per RFC 7231 §4.3.8
        if (request.getMethod() == HttpMethod.TRACE) {
            return handleTrace(request);
        }

        // Handle OPTIONS method per RFC 7231 §4.3.7
        if (request.getMethod() == HttpMethod.OPTIONS) {
            return handleOptions(request);
        }

        var methodMap = findRoute(request.getUri());
        if (methodMap == null) return defaultHandler.handle(ctx, request);
        var handler = methodMap.get(request.getMethod());
        if (handler == null) {
            if (request.getMethod() == HttpMethod.HEAD) {
                handler = methodMap.get(HttpMethod.GET);
            }
            if (handler == null) {
                var response = HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED);
                response.getHeaders().set(HttpHeaders.ALLOW, getAllowedMethods(methodMap));
                return response;
            }
        }
        return handler.handle(ctx, request);
    }

    /**
     * Handles OPTIONS requests by auto-generating the Allow header.
     *
     * <p>Per RFC 7231 §4.3.7, the response lists all supported methods
     * for the requested resource in the Allow header.
     *
     * @param request the OPTIONS request
     * @return the response with Allow header
     */
    private HttpResponse handleOptions(HttpRequest request) {
        String uri = request.getUri();
        if ("*".equals(uri)) {
            // Server-wide OPTIONS
            var response = HttpResponse.of(HttpStatus.OK);
            Set<HttpMethod> allMethods = EnumSet.noneOf(HttpMethod.class);
            for (var methodMap : routes.values()) {
                allMethods.addAll(methodMap.keySet());
            }
            allMethods.add(HttpMethod.OPTIONS);
            if (traceEnabled) allMethods.add(HttpMethod.TRACE);
            response.getHeaders().set(HttpHeaders.ALLOW, formatMethods(allMethods));
            response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, "0");
            return response;
        }

        var methodMap = findRoute(uri);
        if (methodMap == null) {
            return defaultHandler.handle(null, request);
        }
        var response = HttpResponse.of(HttpStatus.OK);
        Set<HttpMethod> methods = EnumSet.noneOf(HttpMethod.class);
        methods.addAll(methodMap.keySet());
        methods.add(HttpMethod.OPTIONS);
        if (methodMap.containsKey(HttpMethod.GET)) {
            methods.add(HttpMethod.HEAD);
        }
        if (traceEnabled) methods.add(HttpMethod.TRACE);
        response.getHeaders().set(HttpHeaders.ALLOW, formatMethods(methods));
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, "0");
        return response;
    }

    /**
     * Handles TRACE requests by reflecting the request message back.
     *
     * <p>Per RFC 7231 §4.3.8, the response body is the complete request
     * message (request-line + headers), and the Content-Type is message/http.
     *
     * @param request the TRACE request
     * @return the response echoing the request
     */
    private HttpResponse handleTrace(HttpRequest request) {
        if (!traceEnabled) {
            return HttpResponse.of(HttpStatus.METHOD_NOT_ALLOWED, "TRACE is disabled");
        }

        // Build the request message to echo back
        var sb = new StringBuilder();
        sb.append(request.getMethod().name())
          .append(" ").append(request.getUri())
          .append(" ").append(request.getVersion().value())
          .append("\r\n");
        for (String name : request.getHeaders().names()) {
            for (String value : request.getHeaders().getAll(name)) {
                sb.append(name).append(": ").append(value).append("\r\n");
            }
        }
        sb.append("\r\n");

        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        var response = HttpResponse.of(HttpStatus.OK);
        response.setBody(ByteBuffer.wrap(body));
        response.getHeaders().set(HttpHeaders.CONTENT_TYPE, "message/http");
        response.getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(body.length));
        return response;
    }

    private Map<HttpMethod, HttpRequestHandler> findRoute(String uri) {
        var path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
        return routes.get(path);
    }

    public Set<String> getRegisteredPaths() {
        return Set.copyOf(routes.keySet());
    }

    /**
     * Returns the allowed methods for the given route as a comma-separated string.
     *
     * @param methodMap the method-to-handler map
     * @return the Allow header value
     */
    private String getAllowedMethods(Map<HttpMethod, HttpRequestHandler> methodMap) {
        return String.join(", ", methodMap.keySet().stream().map(Enum::name).toList());
    }

    private String formatMethods(Set<HttpMethod> methods) {
        return methods.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(", "));
    }
}
