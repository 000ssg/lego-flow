package ssg.legoflow.http.client;

import ssg.legoflow.http.core.*;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
/**
 * Handles HTTP 3xx redirects per RFC 7231 §6.4.
 *
 * <p>Follows redirects for 301, 302, 303, 307, and 308 status codes
 * with configurable maximum redirect count. Handles method changes
 * as specified by each redirect type.
 *
 * @since 0.1.0
 */
public class RedirectHandler {

    /** Status codes that trigger a redirect. */
    private static final Set<Integer> REDIRECT_CODES = Set.of(
            301, 302, 303, 307, 308
    );

    private final int maxRedirects;

    /**
     * Creates a redirect handler with the specified maximum redirects.
     *
     * @param maxRedirects the maximum number of redirects to follow
     */
    public RedirectHandler(int maxRedirects) {
        if (maxRedirects < 0) {
            throw new IllegalArgumentException("maxRedirects must be non-negative");
        }
        this.maxRedirects = maxRedirects;
    }

    /**
     * Creates a redirect handler with the default maximum of 5 redirects.
     */
    public RedirectHandler() {
        this(5);
    }

    /**
     * Returns the maximum number of redirects allowed.
     *
     * @return the max redirects
     */
    public int getMaxRedirects() {
        return maxRedirects;
    }

    /**
     * Determines whether the given response is a redirect.
     *
     * @param response the HTTP response
     * @return true if the response is a redirect (3xx with Location header)
     */
    public boolean isRedirect(HttpResponse response) {
        return REDIRECT_CODES.contains(response.getStatus().code())
                && response.getHeaders().contains(HttpHeaders.LOCATION);
    }

    /**
     * Resolves the redirect URI from the Location header.
     *
     * <p>Handles both absolute and relative URIs.
     *
     * @param originalUri the original request URI
     * @param response    the redirect response
     * @return the resolved redirect URI
     */
    public String resolveRedirectUri(String originalUri, HttpResponse response) {
        String location = response.getHeaders().get(HttpHeaders.LOCATION);
        if (location == null) {
            return null;
        }
        // If the location is absolute, use it directly
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        // Resolve relative URI against the original
        try {
            URI base = URI.create(originalUri);
            URI resolved = base.resolve(location);
            return resolved.toString();
        } catch (Exception e) {
            return location;
        }
    }

    /**
     * Determines the HTTP method for the redirected request.
     *
     * <p>Per RFC 7231:
     * <ul>
     *   <li>301/302: POST may change to GET (common practice)</li>
     *   <li>303: Always changes to GET</li>
     *   <li>307/308: Method must NOT change</li>
     * </ul>
     *
     * @param originalMethod the original request method
     * @param statusCode     the redirect status code
     * @return the method to use for the redirected request
     */
    public HttpMethod getRedirectMethod(HttpMethod originalMethod, int statusCode) {
        return switch (statusCode) {
            case 303 -> HttpMethod.GET;
            case 301, 302 -> {
                // Common practice: POST becomes GET for 301/302
                if (originalMethod == HttpMethod.POST) {
                    yield HttpMethod.GET;
                }
                yield originalMethod;
            }
            case 307, 308 -> originalMethod;
            default -> originalMethod;
        };
    }

    /**
     * Creates a new request for following a redirect.
     *
     * @param originalRequest the original request
     * @param response        the redirect response
     * @return the new request for the redirect target
     */
    public HttpRequest createRedirectRequest(HttpRequest originalRequest, HttpResponse response) {
        String newUri = resolveRedirectUri(originalRequest.getUri(), response);
        if (newUri == null) {
            return null;
        }

        HttpMethod newMethod = getRedirectMethod(
                originalRequest.getMethod(), response.getStatus().code());

        HttpRequest redirectRequest = HttpRequest.of(newMethod, newUri);

        // Copy headers from original request
        for (String name : originalRequest.getHeaders().names()) {
            // Don't copy Host header — it should be derived from the new URI
            if (!"host".equalsIgnoreCase(name)) {
                for (String value : originalRequest.getHeaders().getAll(name)) {
                    redirectRequest.getHeaders().add(name, value);
                }
            }
        }

        // Copy body only if method is preserved (307/308)
        if (newMethod == originalRequest.getMethod()
                && originalRequest.getBody() != null
                && response.getStatus().code() >= 307) {
            redirectRequest.setBody(originalRequest.getBody().duplicate());
        }

        return redirectRequest;
    }

    /**
     * Result of a redirect chain, containing the final response and redirect history.
     *
     * @param response     the final response
     * @param redirectUrls the list of URLs followed during redirection
     * @param redirectCount the number of redirects followed
     * @since 0.1.0
     */
    public record RedirectResult(HttpResponse response, List<String> redirectUrls, int redirectCount) {
        public RedirectResult {
            redirectUrls = Collections.unmodifiableList(new ArrayList<>(redirectUrls));
        }

        /**
         * Returns whether any redirects were followed.
         *
         * @return true if at least one redirect was followed
         */
        public boolean wasRedirected() {
            return redirectCount > 0;
        }
    }

    /**
     * Exception thrown when the maximum redirect count is exceeded.
     *
     * @since 0.1.0
     */
    public static class TooManyRedirectsException extends RuntimeException {
        private final int redirectCount;
        private final List<String> redirectUrls;

        public TooManyRedirectsException(int redirectCount, List<String> redirectUrls) {
            super("Maximum redirects exceeded: " + redirectCount);
            this.redirectCount = redirectCount;
            this.redirectUrls = Collections.unmodifiableList(new ArrayList<>(redirectUrls));
        }

        public int getRedirectCount() { return redirectCount; }
        public List<String> getRedirectUrls() { return redirectUrls; }
    }
}
