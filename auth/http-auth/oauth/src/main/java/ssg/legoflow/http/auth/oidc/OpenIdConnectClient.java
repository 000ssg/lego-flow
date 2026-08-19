package ssg.legoflow.http.auth.oidc;

import ssg.legoflow.http.auth.oauth2.AuthorizationRequest;
import ssg.legoflow.http.auth.oauth2.OAuth2Client;
import ssg.legoflow.http.auth.token.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
/**
 * OpenID Connect client layered on top of OAuth 2.0. Provides discovery,
 * ID Token validation, and UserInfo endpoint support.
 *
 * @since 0.1.0
 */
public class OpenIdConnectClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenIdConnectClient.class);

    private final OAuth2Client oauthClient;
    private final JwtTokenProvider tokenProvider;
    private OidcDiscovery discoveryConfig;

    /**
     * Creates an OpenID Connect client.
     *
     * @param oauthClient   the underlying OAuth 2.0 client
     * @param tokenProvider the JWT provider for ID token validation (may be null if not validating)
     * @since 0.1.0
     */
    public OpenIdConnectClient(OAuth2Client oauthClient, JwtTokenProvider tokenProvider) {
        this.oauthClient = oauthClient;
        this.tokenProvider = tokenProvider;
    }

    /**
     * Creates an OpenID Connect client without token validation.
     *
     * @param oauthClient the underlying OAuth 2.0 client
     * @since 0.1.0
     */
    public OpenIdConnectClient(OAuth2Client oauthClient) {
        this(oauthClient, null);
    }

    /**
     * Sets the discovery configuration (typically fetched from .well-known/openid-configuration).
     *
     * @param discovery the OIDC discovery metadata
     * @since 0.1.0
     */
    public void setDiscoveryConfig(OidcDiscovery discovery) {
        this.discoveryConfig = discovery;
    }

    /**
     * Returns the discovery URL for the configured issuer.
     *
     * @param issuer the issuer URL
     * @return the discovery URL
     * @since 0.1.0
     */
    public String getDiscoveryUrl(String issuer) {
        return OidcDiscovery.discoveryUrl(issuer);
    }

    /**
     * Starts the OpenID Connect authorization code flow with nonce for ID token binding.
     *
     * @return the authorization request with openid scope and nonce
     * @since 0.1.0
     */
    public AuthorizationRequest startAuthenticationFlow() {
        String nonce = generateNonce();
        return oauthClient.startAuthorizationCodeFlowWithPkce()
                .withNonce(nonce)
                .withAdditionalScopes("openid");
    }

    /**
     * Validates an ID token.
     *
     * @param rawIdToken the raw JWT ID token
     * @return the validated claims, or empty if invalid
     * @since 0.1.0
     */
    public Optional<Map<String, Object>> validateIdToken(String rawIdToken) {
        if (tokenProvider == null) {
            LOG.warn("No token provider configured for ID token validation");
            return Optional.empty();
        }
        return tokenProvider.validateToken(rawIdToken);
    }

    /**
     * Parses an ID token without validation.
     *
     * @param rawIdToken the raw JWT
     * @return the parsed ID token, or empty
     * @since 0.1.0
     */
    public Optional<IdToken> parseIdToken(String rawIdToken) {
        return IdToken.parse(rawIdToken);
    }

    /**
     * Parses a UserInfo response.
     *
     * @param responseBody the JSON response body
     * @return the UserInfo
     * @since 0.1.0
     */
    public UserInfo parseUserInfo(String responseBody) {
        return UserInfo.fromJson(responseBody);
    }

    /**
     * Returns the discovery configuration.
     *
     * @return the discovery config, or null if not set
     * @since 0.1.0
     */
    public OidcDiscovery getDiscoveryConfig() {
        return discoveryConfig;
    }

    /**
     * Returns the underlying OAuth 2.0 client.
     *
     * @return the OAuth client
     * @since 0.1.0
     */
    public OAuth2Client getOAuthClient() {
        return oauthClient;
    }

    private String generateNonce() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
