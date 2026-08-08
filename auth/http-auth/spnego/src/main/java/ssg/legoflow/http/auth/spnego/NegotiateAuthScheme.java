package ssg.legoflow.http.auth.spnego;

import ssg.legoflow.auth.gssapi.GssContextFactory;
import ssg.legoflow.auth.gssapi.GssContextWrapper;
import ssg.legoflow.auth.gssapi.GssException;
import ssg.legoflow.auth.gssapi.SpnegoTokenHandler;
import ssg.legoflow.http.auth.AuthContext;
import ssg.legoflow.http.auth.AuthCredentials;
import ssg.legoflow.http.auth.AuthPrincipal;
import ssg.legoflow.http.auth.AuthResult;
import ssg.legoflow.http.auth.AuthenticationScheme;
import ssg.legoflow.http.core.HttpHeaders;
import ssg.legoflow.http.core.HttpRequest;
import ssg.legoflow.http.core.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.Objects;

/**
 * HTTP Negotiate (SPNEGO) authentication scheme per RFC 4559.
 *
 * <p>Implements the "Negotiate" HTTP authentication scheme, which uses SPNEGO
 * to negotiate the underlying authentication mechanism (typically Kerberos V5).
 * The authentication flow is:</p>
 * <ol>
 *   <li>Server sends 401 with WWW-Authenticate: Negotiate</li>
 *   <li>Client sends Authorization: Negotiate &lt;base64-SPNEGO-token&gt;</li>
 *   <li>Server processes the SPNEGO token through GSS-API</li>
 *   <li>If context is established, authentication succeeds; otherwise returns a challenge
 *       with a response token</li>
 * </ol>
 *
 * @since 0.1.0
 */
public final class NegotiateAuthScheme implements AuthenticationScheme {

    private static final Logger LOG = LoggerFactory.getLogger(NegotiateAuthScheme.class);
    private static final String SCHEME_NAME = "Negotiate";
    private static final String NEGOTIATE_PREFIX = "Negotiate ";

    private final SpnegoConfig config;

    /**
     * Creates a Negotiate authentication scheme with the given SPNEGO configuration.
     *
     * @param config the SPNEGO configuration
     * @throws NullPointerException if config is null
     * @since 0.1.0
     */
    public NegotiateAuthScheme(SpnegoConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public String schemeName() {
        return SCHEME_NAME;
    }

    /**
     * Authenticates an HTTP request using the Negotiate scheme.
     *
     * <p>Extracts the SPNEGO token from the Authorization header, creates a server-side
     * GSS context, and processes the token. If the context is fully established, returns
     * a success result with the authenticated principal. Otherwise returns a challenge
     * with a continuation token.</p>
     *
     * @param request the HTTP request containing the Authorization header
     * @param context the authentication context
     * @return Success if authenticated, Challenge if more tokens needed, Failure on error
     * @since 0.1.0
     */
    @Override
    public AuthResult authenticate(HttpRequest request, AuthContext context) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.regionMatches(true, 0, NEGOTIATE_PREFIX, 0, NEGOTIATE_PREFIX.length())) {
            return AuthResult.challenge(SCHEME_NAME);
        }

        String base64Token = authHeader.substring(NEGOTIATE_PREFIX.length()).trim();
        if (base64Token.isEmpty()) {
            return AuthResult.challenge(SCHEME_NAME);
        }

        byte[] tokenBytes;
        try {
            tokenBytes = Base64.getDecoder().decode(base64Token);
        } catch (IllegalArgumentException e) {
            LOG.debug("Invalid Base64 in Negotiate token: {}", e.getMessage());
            return AuthResult.failure("Invalid Negotiate token encoding");
        }

        // Extract the inner mechanism token if this is a SPNEGO wrapper
        byte[] mechToken;
        try {
            if (SpnegoTokenHandler.isSpnegoToken(tokenBytes)) {
                mechToken = SpnegoTokenHandler.extractMechToken(tokenBytes);
            } else {
                mechToken = tokenBytes;
            }
        } catch (GssException e) {
            LOG.debug("Failed to extract mechanism token: {}", e.getMessage());
            return AuthResult.failure("Invalid SPNEGO token format");
        }

        try (GssContextWrapper gssContext = GssContextFactory.createServerContext(config.gssConfig())) {
            byte[] responseToken = gssContext.acceptSecContext(mechToken);

            if (gssContext.isEstablished()) {
                String srcName = gssContext.getSrcName();
                String principalName = config.stripRealmFromPrincipal()
                        ? stripRealm(srcName) : srcName;

                LOG.debug("SPNEGO authentication successful for: {}", principalName);
                return AuthResult.success(AuthPrincipal.of(principalName));
            } else {
                // Context not yet established — send continuation token
                LOG.debug("SPNEGO context not yet established, sending continuation token");
                return AuthResult.challenge(SCHEME_NAME);
            }
        } catch (GssException e) {
            LOG.debug("SPNEGO authentication failed: {}", e.getMessage());
            return AuthResult.failure("SPNEGO authentication failed: " + e.getMessage());
        }
    }

    /**
     * Adds a WWW-Authenticate: Negotiate challenge header to the response.
     *
     * @param response the HTTP response to add the challenge to
     * @param context  the authentication context
     * @since 0.1.0
     */
    @Override
    public void challenge(HttpResponse response, AuthContext context) {
        response.getHeaders().add(HttpHeaders.WWW_AUTHENTICATE, SCHEME_NAME);
    }

    /**
     * Extracts Negotiate credentials from the request. Returns a Bearer-style
     * credential containing the Base64-encoded SPNEGO token, or None if no
     * Negotiate header is present.
     *
     * @param request the HTTP request
     * @return Bearer credentials with the token, or None
     * @since 0.1.0
     */
    @Override
    public AuthCredentials extractCredentials(HttpRequest request) {
        String authHeader = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, NEGOTIATE_PREFIX, 0, NEGOTIATE_PREFIX.length())) {
            String token = authHeader.substring(NEGOTIATE_PREFIX.length()).trim();
            if (!token.isEmpty()) {
                return new AuthCredentials.Bearer(token);
            }
        }
        return new AuthCredentials.None();
    }

    /**
     * Strips the realm portion from a Kerberos principal name.
     * For example, "user@EXAMPLE.COM" becomes "user".
     *
     * @param principal the full principal name
     * @return the principal without the realm
     */
    private static String stripRealm(String principal) {
        int atIndex = principal.indexOf('@');
        return atIndex > 0 ? principal.substring(0, atIndex) : principal;
    }
}
