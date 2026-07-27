package ssg.legoflow.http.auth.oauth2;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * OAuth 2.0 authorization request builder. Constructs the URL for the authorization
 * endpoint with all required and optional parameters.
 *
 * @since 1.0.0
 */
public class AuthorizationRequest {

    private final OAuth2Config config;
    private final String state;
    private final String responseType;
    private PkceChallenge pkceChallenge;
    private String nonce;
    private String additionalScopes;

    /**
     * Creates an authorization request.
     *
     * @param config       the OAuth2 configuration
     * @param responseType the response type (e.g., "code")
     * @since 1.0.0
     */
    public AuthorizationRequest(OAuth2Config config, String responseType) {
        this.config = Objects.requireNonNull(config);
        this.responseType = Objects.requireNonNull(responseType);
        this.state = generateState();
    }

    /**
     * Creates an authorization code request.
     *
     * @param config the OAuth2 configuration
     * @return the authorization request
     * @since 1.0.0
     */
    public static AuthorizationRequest authorizationCode(OAuth2Config config) {
        return new AuthorizationRequest(config, "code");
    }

    /**
     * Adds PKCE to the request.
     *
     * @param challenge the PKCE challenge
     * @return this request for chaining
     * @since 1.0.0
     */
    public AuthorizationRequest withPkce(PkceChallenge challenge) {
        this.pkceChallenge = challenge;
        return this;
    }

    /**
     * Adds an OpenID Connect nonce to the request.
     *
     * @param nonce the nonce
     * @return this request for chaining
     * @since 1.0.0
     */
    public AuthorizationRequest withNonce(String nonce) {
        this.nonce = nonce;
        return this;
    }

    /**
     * Adds additional scopes beyond those configured.
     *
     * @param scopes space-separated additional scopes
     * @return this request for chaining
     * @since 1.0.0
     */
    public AuthorizationRequest withAdditionalScopes(String scopes) {
        this.additionalScopes = scopes;
        return this;
    }

    /**
     * Builds the authorization URL.
     *
     * @return the full authorization URL
     * @since 1.0.0
     */
    public String buildUrl() {
        var sb = new StringBuilder(config.getAuthorizationEndpoint());
        sb.append("?response_type=").append(encode(responseType));
        sb.append("&client_id=").append(encode(config.getClientId()));

        if (config.getRedirectUri() != null) {
            sb.append("&redirect_uri=").append(encode(config.getRedirectUri()));
        }

        String scopeStr = buildScopeString();
        if (!scopeStr.isEmpty()) {
            sb.append("&scope=").append(encode(scopeStr));
        }

        sb.append("&state=").append(encode(state));

        if (pkceChallenge != null) {
            sb.append("&code_challenge=").append(encode(pkceChallenge.getCodeChallenge()));
            sb.append("&code_challenge_method=").append(encode(pkceChallenge.getChallengeMethod()));
        }

        if (nonce != null) {
            sb.append("&nonce=").append(encode(nonce));
        }

        return sb.toString();
    }

    private String buildScopeString() {
        var sb = new StringBuilder();
        for (String scope : config.getScopes()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(scope);
        }
        if (additionalScopes != null && !additionalScopes.isEmpty()) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(additionalScopes);
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String generateState() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Getters

    /** Returns the state parameter for CSRF protection. */
    public String getState() { return state; }
    /** Returns the response type. */
    public String getResponseType() { return responseType; }
    /** Returns the PKCE challenge, if set. */
    public PkceChallenge getPkceChallenge() { return pkceChallenge; }
    /** Returns the OAuth2 configuration. */
    public OAuth2Config getConfig() { return config; }
}
