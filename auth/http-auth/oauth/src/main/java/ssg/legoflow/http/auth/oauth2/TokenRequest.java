package ssg.legoflow.http.auth.oauth2;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * OAuth 2.0 token exchange request. Encodes the parameters for a POST to the token endpoint.
 *
 * @since 0.1.0
 */
public class TokenRequest {

    /** Authorization code grant type. */
    public static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    /** Client credentials grant type. */
    public static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    /** Resource owner password grant type. */
    public static final String GRANT_PASSWORD = "password";
    /** Refresh token grant type. */
    public static final String GRANT_REFRESH_TOKEN = "refresh_token";

    private final String grantType;
    private final Map<String, String> parameters = new LinkedHashMap<>();

    /**
     * Creates a token request.
     *
     * @param grantType the grant type
     * @since 0.1.0
     */
    public TokenRequest(String grantType) {
        this.grantType = Objects.requireNonNull(grantType);
        parameters.put("grant_type", grantType);
    }

    /**
     * Creates an authorization code token request.
     *
     * @param code        the authorization code
     * @param redirectUri the redirect URI
     * @param config      the OAuth2 configuration
     * @return the token request
     * @since 0.1.0
     */
    public static TokenRequest authorizationCode(String code, String redirectUri, OAuth2Config config) {
        var req = new TokenRequest(GRANT_AUTHORIZATION_CODE);
        req.parameter("code", code);
        req.parameter("redirect_uri", redirectUri);
        req.parameter("client_id", config.getClientId());
        if (config.getClientSecret() != null) {
            req.parameter("client_secret", config.getClientSecret());
        }
        return req;
    }

    /**
     * Creates an authorization code token request with PKCE.
     *
     * @param code         the authorization code
     * @param redirectUri  the redirect URI
     * @param codeVerifier the PKCE code verifier
     * @param config       the OAuth2 configuration
     * @return the token request
     * @since 0.1.0
     */
    public static TokenRequest authorizationCodeWithPkce(String code, String redirectUri,
                                                          String codeVerifier, OAuth2Config config) {
        var req = authorizationCode(code, redirectUri, config);
        req.parameter("code_verifier", codeVerifier);
        return req;
    }

    /**
     * Creates a client credentials token request.
     *
     * @param config the OAuth2 configuration
     * @return the token request
     * @since 0.1.0
     */
    public static TokenRequest clientCredentials(OAuth2Config config) {
        var req = new TokenRequest(GRANT_CLIENT_CREDENTIALS);
        req.parameter("client_id", config.getClientId());
        if (config.getClientSecret() != null) {
            req.parameter("client_secret", config.getClientSecret());
        }
        if (!config.getScopes().isEmpty()) {
            req.parameter("scope", String.join(" ", config.getScopes()));
        }
        return req;
    }

    /**
     * Creates a resource owner password token request.
     *
     * @param username the username
     * @param password the password
     * @param config   the OAuth2 configuration
     * @return the token request
     * @since 0.1.0
     */
    public static TokenRequest password(String username, String password, OAuth2Config config) {
        var req = new TokenRequest(GRANT_PASSWORD);
        req.parameter("username", username);
        req.parameter("password", password);
        req.parameter("client_id", config.getClientId());
        if (config.getClientSecret() != null) {
            req.parameter("client_secret", config.getClientSecret());
        }
        if (!config.getScopes().isEmpty()) {
            req.parameter("scope", String.join(" ", config.getScopes()));
        }
        return req;
    }

    /**
     * Creates a refresh token request.
     *
     * @param refreshToken the refresh token
     * @param config       the OAuth2 configuration
     * @return the token request
     * @since 0.1.0
     */
    public static TokenRequest refreshToken(String refreshToken, OAuth2Config config) {
        var req = new TokenRequest(GRANT_REFRESH_TOKEN);
        req.parameter("refresh_token", refreshToken);
        req.parameter("client_id", config.getClientId());
        if (config.getClientSecret() != null) {
            req.parameter("client_secret", config.getClientSecret());
        }
        return req;
    }

    /**
     * Adds a parameter.
     *
     * @param name  the parameter name
     * @param value the parameter value
     * @return this request for chaining
     * @since 0.1.0
     */
    public TokenRequest parameter(String name, String value) {
        parameters.put(name, value);
        return this;
    }

    /**
     * Encodes parameters as application/x-www-form-urlencoded body.
     *
     * @return the encoded body
     * @since 0.1.0
     */
    public String toFormBody() {
        var sb = new StringBuilder();
        for (var entry : parameters.entrySet()) {
            if (!sb.isEmpty()) sb.append('&');
            sb.append(encode(entry.getKey())).append('=').append(encode(entry.getValue()));
        }
        return sb.toString();
    }

    /**
     * Returns the parameters as a map.
     *
     * @return the parameters
     * @since 0.1.0
     */
    public Map<String, String> getParameters() {
        return Map.copyOf(parameters);
    }

    /**
     * Returns the grant type.
     *
     * @return the grant type
     * @since 0.1.0
     */
    public String getGrantType() {
        return grantType;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
