package ssg.legoflow.http.proxy.auth;

import ssg.legoflow.http.core.*;
import ssg.legoflow.http.proxy.ProxyHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP Basic authentication for proxy (Proxy-Authorization header).
 *
 * <p>Implements HTTP Basic authentication per RFC 7617, using the
 * Proxy-Authorization request header and the Proxy-Authenticate
 * response header per RFC 7235 section 4.3-4.4.</p>
 *
 * @since 1.0.0
 */
public class BasicProxyAuth implements ProxyAuthenticator {

    private static final Logger LOG = LoggerFactory.getLogger(BasicProxyAuth.class);

    private final String realm;
    private final Map<String, String> credentials = new ConcurrentHashMap<>();

    /**
     * Creates a new Basic proxy authenticator with the given realm.
     *
     * @param realm the authentication realm
     * @since 1.0.0
     */
    public BasicProxyAuth(String realm) {
        this.realm = realm;
    }

    /**
     * Adds a username/password pair.
     *
     * @param username the username
     * @param password the password
     * @since 1.0.0
     */
    public void addUser(String username, String password) {
        credentials.put(username, password);
    }

    /**
     * Removes a user.
     *
     * @param username the username to remove
     * @since 1.0.0
     */
    public void removeUser(String username) {
        credentials.remove(username);
    }

    @Override
    public boolean authenticate(HttpRequest request) {
        String authHeader = request.getHeaders().get(ProxyHeaders.PROXY_AUTHORIZATION);
        if (authHeader == null || !authHeader.toLowerCase().startsWith("basic ")) {
            LOG.debug("No Proxy-Authorization header or not Basic scheme");
            return false;
        }

        String encoded = authHeader.substring(6).trim();
        try {
            String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
            int colonIdx = decoded.indexOf(':');
            if (colonIdx < 0) {
                LOG.debug("Invalid Basic credentials format");
                return false;
            }
            String username = decoded.substring(0, colonIdx);
            String password = decoded.substring(colonIdx + 1);

            String expected = credentials.get(username);
            if (expected != null && expected.equals(password)) {
                LOG.debug("Proxy authentication successful for user: {}", username);
                return true;
            }
            LOG.debug("Proxy authentication failed for user: {}", username);
            return false;
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid Base64 in Proxy-Authorization header");
            return false;
        }
    }

    @Override
    public HttpResponse createChallenge() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(ProxyHeaders.PROXY_AUTHENTICATE, "Basic realm=\"" + realm + "\"");
        headers.set(HttpHeaders.CONTENT_LENGTH, "0");
        return new HttpResponse(HttpStatus.PROXY_AUTHENTICATION_REQUIRED, HttpVersion.HTTP_1_1, headers);
    }

    @Override
    public String getScheme() {
        return "Basic";
    }

    /**
     * Returns the realm.
     *
     * @return the authentication realm
     * @since 1.0.0
     */
    public String getRealm() {
        return realm;
    }

    /**
     * Returns the number of registered users.
     *
     * @return the user count
     * @since 1.0.0
     */
    public int getUserCount() {
        return credentials.size();
    }

    /**
     * Creates a Proxy-Authorization header value for the given credentials.
     * Utility method for client-side use.
     *
     * @param username the username
     * @param password the password
     * @return the header value
     * @since 1.0.0
     */
    public static String encodeCredentials(String username, String password) {
        String combined = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(
                combined.getBytes(StandardCharsets.UTF_8));
    }
}
