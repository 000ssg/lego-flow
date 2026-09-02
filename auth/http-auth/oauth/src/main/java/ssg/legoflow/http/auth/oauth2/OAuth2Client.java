package ssg.legoflow.http.auth.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.Optional;
/**
 * OAuth 2.0 client supporting Authorization Code, Client Credentials, Resource Owner
 * Password, Refresh Token flows, and PKCE extension (RFC 7636).
 *
 * <p>This client constructs the authorization requests and token exchange requests.
 * The actual HTTP transport is delegated to the existing http module's HttpClient.</p>
 *
 * @since 0.1.0
 */
public class OAuth2Client {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2Client.class);

    private final OAuth2Config config;

    /**
     * Creates an OAuth 2.0 client.
     *
     * @param config the OAuth2 configuration
     * @since 0.1.0
     */
    public OAuth2Client(OAuth2Config config) {
        this.config = Objects.requireNonNull(config);
    }

    /**
     * Starts the Authorization Code flow by building the authorization URL.
     *
     * @return the authorization request with URL and state
     * @since 0.1.0
     */
    public AuthorizationRequest startAuthorizationCodeFlow() {
        return AuthorizationRequest.authorizationCode(config);
    }

    /**
     * Starts the Authorization Code flow with PKCE.
     *
     * @return the authorization request with PKCE challenge
     * @since 0.1.0
     */
    public AuthorizationRequest startAuthorizationCodeFlowWithPkce() {
        PkceChallenge pkce = PkceChallenge.generateS256();
        return AuthorizationRequest.authorizationCode(config).withPkce(pkce);
    }

    /**
     * Exchanges an authorization code for tokens.
     *
     * @param code        the authorization code
     * @param redirectUri the redirect URI used in the authorization request
     * @return the token request to send to the token endpoint
     * @since 0.1.0
     */
    public TokenRequest exchangeAuthorizationCode(String code, String redirectUri) {
        LOG.debug("Exchanging authorization code for tokens");
        return TokenRequest.authorizationCode(code, redirectUri, config);
    }

    /**
     * Exchanges an authorization code for tokens with PKCE verification.
     *
     * @param code         the authorization code
     * @param redirectUri  the redirect URI
     * @param codeVerifier the PKCE code verifier
     * @return the token request
     * @since 0.1.0
     */
    public TokenRequest exchangeAuthorizationCodeWithPkce(String code, String redirectUri,
                                                           String codeVerifier) {
        LOG.debug("Exchanging authorization code with PKCE for tokens");
        return TokenRequest.authorizationCodeWithPkce(code, redirectUri, codeVerifier, config);
    }

    /**
     * Creates a Client Credentials token request.
     *
     * @return the token request
     * @since 0.1.0
     */
    public TokenRequest clientCredentialsGrant() {
        LOG.debug("Requesting client credentials token");
        return TokenRequest.clientCredentials(config);
    }

    /**
     * Creates a Resource Owner Password token request.
     *
     * @param username the username
     * @param password the password
     * @return the token request
     * @since 0.1.0
     */
    public TokenRequest passwordGrant(String username, String password) {
        LOG.debug("Requesting resource owner password token for user: {}", username);
        return TokenRequest.password(username, password, config);
    }

    /**
     * Creates a Refresh Token request.
     *
     * @param refreshToken the refresh token
     * @return the token request
     * @since 0.1.0
     */
    public TokenRequest refreshTokenGrant(String refreshToken) {
        LOG.debug("Refreshing access token");
        return TokenRequest.refreshToken(refreshToken, config);
    }

    /**
     * Parses a token response from the token endpoint response body.
     *
     * @param responseBody the response body (JSON)
     * @return the token response if successful, empty if error
     * @since 0.1.0
     */
    public Optional<OAuth2TokenResponse> parseTokenResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return Optional.empty();
        if (responseBody.contains("\"error\"")) {
            OAuth2Error error = OAuth2Error.fromJson(responseBody);
            LOG.warn("OAuth2 error: {} - {}", error.error(), error.errorDescription());
            return Optional.empty();
        }
        return Optional.of(OAuth2TokenResponse.fromJson(responseBody));
    }

    /**
     * Returns the configuration.
     *
     * @return the OAuth2 config
     * @since 0.1.0
     */
    public OAuth2Config getConfig() {
        return config;
    }
}
