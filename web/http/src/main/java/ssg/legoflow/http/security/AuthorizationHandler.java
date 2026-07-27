package ssg.legoflow.http.security;

import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import ssg.legoflow.http.core.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

/**
 * Handles Authorization header parsing and authentication per RFC 7235 §4.1.
 *
 * <p>Supports Basic and Bearer authentication schemes. Parses the Authorization
 * header from requests and generates WWW-Authenticate challenges for 401 responses.
 *
 * @since 1.0.0
 */
public class AuthorizationHandler {

    /**
     * Represents parsed authorization credentials.
     *
     * @param scheme     the authentication scheme (e.g., "Basic", "Bearer")
     * @param credentials the credential string
     * @since 1.0.0
     */
    public record Credentials(String scheme, String credentials) {
        public Credentials {
            Objects.requireNonNull(scheme, "scheme must not be null");
            Objects.requireNonNull(credentials, "credentials must not be null");
        }
    }

    /**
     * Represents decoded Basic authentication credentials.
     *
     * @param username the username
     * @param password the password
     * @since 1.0.0
     */
    public record BasicCredentials(String username, String password) {
        public BasicCredentials {
            Objects.requireNonNull(username, "username must not be null");
            Objects.requireNonNull(password, "password must not be null");
        }
    }

    /**
     * Parses the Authorization header from a request.
     *
     * @param request the HTTP request
     * @return the parsed credentials, or null if no Authorization header is present
     */
    public Credentials parseAuthorization(HttpRequest request) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        return parseAuthorizationHeader(authHeader);
    }

    /**
     * Parses an Authorization header value.
     *
     * @param headerValue the Authorization header value
     * @return the parsed credentials, or null if the value is null or invalid
     */
    public Credentials parseAuthorizationHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String trimmed = headerValue.strip();
        int spaceIndex = trimmed.indexOf(' ');
        if (spaceIndex < 0) {
            return null;
        }
        String scheme = trimmed.substring(0, spaceIndex);
        String credentials = trimmed.substring(spaceIndex + 1).strip();
        return new Credentials(scheme, credentials);
    }

    /**
     * Decodes Basic authentication credentials from the Authorization header.
     *
     * @param request the HTTP request
     * @return the decoded BasicCredentials, or null if not Basic auth or invalid
     */
    public BasicCredentials parseBasicAuth(HttpRequest request) {
        Credentials creds = parseAuthorization(request);
        if (creds == null || !"Basic".equalsIgnoreCase(creds.scheme())) {
            return null;
        }
        return decodeBasicCredentials(creds.credentials());
    }

    /**
     * Decodes a Base64-encoded Basic auth credential string.
     *
     * @param encoded the Base64 encoded credentials
     * @return the decoded BasicCredentials, or null if invalid
     */
    public BasicCredentials decodeBasicCredentials(String encoded) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String decodedStr = new String(decoded, StandardCharsets.UTF_8);
            int colonIndex = decodedStr.indexOf(':');
            if (colonIndex < 0) {
                return null;
            }
            return new BasicCredentials(
                    decodedStr.substring(0, colonIndex),
                    decodedStr.substring(colonIndex + 1));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Extracts a Bearer token from the Authorization header.
     *
     * @param request the HTTP request
     * @return the bearer token string, or null if not Bearer auth
     */
    public String parseBearerToken(HttpRequest request) {
        Credentials creds = parseAuthorization(request);
        if (creds == null || !"Bearer".equalsIgnoreCase(creds.scheme())) {
            return null;
        }
        return creds.credentials();
    }

    /**
     * Encodes Basic authentication credentials.
     *
     * @param username the username
     * @param password the password
     * @return the full Authorization header value (e.g., "Basic dXNlcjpwYXNz")
     */
    public String encodeBasicAuth(String username, String password) {
        String combined = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(
                combined.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    /**
     * Creates a 401 Unauthorized response with a WWW-Authenticate challenge.
     *
     * @param scheme the authentication scheme (e.g., "Basic", "Bearer")
     * @param realm  the protection realm
     * @return the 401 response with WWW-Authenticate header
     */
    public HttpResponse unauthorizedResponse(String scheme, String realm) {
        HttpResponse response = HttpResponse.of(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                scheme + " realm=\"" + realm + "\"");
        return response;
    }
}
