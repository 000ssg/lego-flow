package ssg.legoflow.http.auth.bearer;

import ssg.legoflow.http.auth.*;
import ssg.legoflow.http.auth.oauth2.server.TokenStore;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * OAuth 2.0 Bearer Token authentication scheme (RFC 6750).
 * Extracts Bearer tokens from the Authorization header and validates them
 * either via JWT verification or token store introspection.
 *
 * @since 0.1.0
 */
public class BearerAuthScheme implements AuthenticationScheme {

    private static final Logger LOG = LoggerFactory.getLogger(BearerAuthScheme.class);
    private static final String SCHEME_NAME = "Bearer";

    private final JwtTokenProvider jwtProvider;
    private final TokenStore tokenStore;

    /**
     * Creates a Bearer auth scheme with JWT validation.
     *
     * @param jwtProvider the JWT provider for token validation
     * @since 0.1.0
     */
    public BearerAuthScheme(JwtTokenProvider jwtProvider) {
        this.jwtProvider = jwtProvider;
        this.tokenStore = null;
    }

    /**
     * Creates a Bearer auth scheme with token store introspection.
     *
     * @param tokenStore the token store for validation
     * @since 0.1.0
     */
    public BearerAuthScheme(TokenStore tokenStore) {
        this.jwtProvider = null;
        this.tokenStore = tokenStore;
    }

    /**
     * Creates a Bearer auth scheme with both JWT and token store validation.
     * JWT is tried first; if it fails, the token store is checked.
     *
     * @param jwtProvider the JWT provider
     * @param tokenStore  the token store
     * @since 0.1.0
     */
    public BearerAuthScheme(JwtTokenProvider jwtProvider, TokenStore tokenStore) {
        this.jwtProvider = jwtProvider;
        this.tokenStore = tokenStore;
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

        if (creds instanceof AuthCredentials.Bearer bearer) {
            // Try JWT validation first
            if (jwtProvider != null) {
                Optional<Map<String, Object>> claims = jwtProvider.validateToken(bearer.token());
                if (claims.isPresent()) {
                    String subject = (String) claims.get().get("sub");
                    if (subject != null) {
                        LOG.debug("Bearer JWT auth successful for subject: {}", subject);
                        return AuthResult.success(AuthPrincipal.of(subject));
                    }
                }
            }

            // Fall back to token store
            if (tokenStore != null) {
                var stored = tokenStore.validateAccessToken(bearer.token());
                if (stored.isPresent()) {
                    LOG.debug("Bearer token store auth successful for subject: {}",
                            stored.get().subject());
                    return AuthResult.success(AuthPrincipal.of(stored.get().subject(),
                            stored.get().scopes()));
                }
            }

            LOG.debug("Bearer token validation failed");
            return AuthResult.failure("Invalid or expired bearer token");
        }

        return AuthResult.failure("Unexpected credential type");
    }

    @Override
    public void challenge(HttpResponse response, AuthContext context) {
        String value = SCHEME_NAME + " realm=\"" + context.getRealm() + "\"";
        response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, value);
    }

    @Override
    public AuthCredentials extractCredentials(HttpRequest request) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return new AuthCredentials.None();
        }
        String token = authHeader.substring(7).trim();
        if (token.isEmpty()) {
            return new AuthCredentials.None();
        }
        return new AuthCredentials.Bearer(token);
    }

    /**
     * Creates an Authorization header value for a bearer token.
     *
     * @param token the bearer token
     * @return the header value
     * @since 0.1.0
     */
    public static String encodeToken(String token) {
        return SCHEME_NAME + " " + token;
    }
}
