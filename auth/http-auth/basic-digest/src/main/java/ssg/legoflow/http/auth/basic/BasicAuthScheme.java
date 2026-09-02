package ssg.legoflow.http.auth.basic;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
/**
 * HTTP Basic Authentication scheme (RFC 7617).
 * Decodes Base64-encoded username:password from the Authorization header
 * and validates against the user store.
 *
 * @since 0.1.0
 */
public class BasicAuthScheme implements AuthenticationScheme {

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthScheme.class);
    private static final String SCHEME_NAME = "Basic";

    private final BasicUserStore userStore;

    /**
     * Creates a Basic authentication scheme with a user store.
     *
     * @param userStore the user store for credential validation
     * @since 0.1.0
     */
    public BasicAuthScheme(BasicUserStore userStore) {
        this.userStore = userStore;
    }

    @Override
    public String schemeName() {
        return SCHEME_NAME;
    }

    @Override
    public AuthResult authenticate(HttpRequest request, AuthContext context) {
        AuthCredentials creds = extractCredentials(request);

        if (creds instanceof AuthCredentials.None) {
            return AuthResult.challenge(SCHEME_NAME);
        }

        if (creds instanceof AuthCredentials.Basic basic) {
            Optional<AuthPrincipal> principal = userStore.authenticate(basic.username(), basic.password());
            if (principal.isPresent()) {
                LOG.debug("Basic auth successful for user: {}", basic.username());
                return AuthResult.success(principal.get());
            } else {
                LOG.debug("Basic auth failed for user: {}", basic.username());
                return AuthResult.failure("Invalid credentials");
            }
        }

        return AuthResult.failure("Unexpected credential type");
    }

    @Override
    public void challenge(HttpResponse response, AuthContext context) {
        String realm = context.getRealm();
        response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE,
                SCHEME_NAME + " realm=\"" + realm + "\", charset=\"UTF-8\"");
    }

    @Override
    public AuthCredentials extractCredentials(HttpRequest request) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Basic ", 0, 6)) {
            return new AuthCredentials.None();
        }

        String encoded = authHeader.substring(6).trim();
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String credentials = new String(decoded, StandardCharsets.UTF_8);
            int colonIndex = credentials.indexOf(':');
            if (colonIndex < 0) {
                return new AuthCredentials.None();
            }
            String username = credentials.substring(0, colonIndex);
            String password = credentials.substring(colonIndex + 1);
            return new AuthCredentials.Basic(username, password);
        } catch (IllegalArgumentException e) {
            LOG.debug("Failed to decode Basic credentials: {}", e.getMessage());
            return new AuthCredentials.None();
        }
    }

    /**
     * Encodes credentials for use in an Authorization header.
     *
     * @param username the username
     * @param password the password
     * @return the Authorization header value (e.g., "Basic dXNlcjpwYXNz")
     * @since 0.1.0
     */
    public static String encodeCredentials(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8));
        return SCHEME_NAME + " " + encoded;
    }
}
