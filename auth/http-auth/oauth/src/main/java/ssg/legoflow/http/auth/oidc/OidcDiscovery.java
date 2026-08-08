package ssg.legoflow.http.auth.oidc;

import java.util.List;
import java.util.Objects;

/**
 * OpenID Provider metadata from .well-known/openid-configuration.
 *
 * @since 0.1.0
 */
public class OidcDiscovery {

    private final String issuer;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String userInfoEndpoint;
    private final String jwksUri;
    private final List<String> responseTypesSupported;
    private final List<String> scopesSupported;
    private final List<String> idTokenSigningAlgValuesSupported;

    /**
     * Creates an OIDC discovery configuration.
     *
     * @param issuer                          the issuer identifier
     * @param authorizationEndpoint           the authorization endpoint URL
     * @param tokenEndpoint                   the token endpoint URL
     * @param userInfoEndpoint                the UserInfo endpoint URL
     * @param jwksUri                         the JWKS URI
     * @param responseTypesSupported          supported response types
     * @param scopesSupported                 supported scopes
     * @param idTokenSigningAlgValuesSupported supported ID token signing algorithms
     * @since 0.1.0
     */
    public OidcDiscovery(String issuer, String authorizationEndpoint, String tokenEndpoint,
                         String userInfoEndpoint, String jwksUri,
                         List<String> responseTypesSupported, List<String> scopesSupported,
                         List<String> idTokenSigningAlgValuesSupported) {
        this.issuer = Objects.requireNonNull(issuer);
        this.authorizationEndpoint = authorizationEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.userInfoEndpoint = userInfoEndpoint;
        this.jwksUri = jwksUri;
        this.responseTypesSupported = responseTypesSupported != null ? List.copyOf(responseTypesSupported) : List.of();
        this.scopesSupported = scopesSupported != null ? List.copyOf(scopesSupported) : List.of();
        this.idTokenSigningAlgValuesSupported = idTokenSigningAlgValuesSupported != null
                ? List.copyOf(idTokenSigningAlgValuesSupported) : List.of();
    }

    /**
     * Constructs the discovery URL from an issuer.
     *
     * @param issuer the issuer URL
     * @return the discovery URL
     * @since 0.1.0
     */
    public static String discoveryUrl(String issuer) {
        String base = issuer.endsWith("/") ? issuer.substring(0, issuer.length() - 1) : issuer;
        return base + "/.well-known/openid-configuration";
    }

    /**
     * Serializes to JSON.
     *
     * @return the JSON string
     * @since 0.1.0
     */
    public String toJson() {
        var sb = new StringBuilder("{");
        sb.append("\"issuer\":\"").append(issuer).append("\"");
        if (authorizationEndpoint != null)
            sb.append(",\"authorization_endpoint\":\"").append(authorizationEndpoint).append("\"");
        if (tokenEndpoint != null)
            sb.append(",\"token_endpoint\":\"").append(tokenEndpoint).append("\"");
        if (userInfoEndpoint != null)
            sb.append(",\"userinfo_endpoint\":\"").append(userInfoEndpoint).append("\"");
        if (jwksUri != null)
            sb.append(",\"jwks_uri\":\"").append(jwksUri).append("\"");
        sb.append("}");
        return sb.toString();
    }

    // Getters

    public String getIssuer() { return issuer; }
    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public String getUserInfoEndpoint() { return userInfoEndpoint; }
    public String getJwksUri() { return jwksUri; }
    public List<String> getResponseTypesSupported() { return responseTypesSupported; }
    public List<String> getScopesSupported() { return scopesSupported; }
    public List<String> getIdTokenSigningAlgValuesSupported() { return idTokenSigningAlgValuesSupported; }
}
