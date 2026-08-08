package ssg.legoflow.http.auth.oauth2;

import java.util.Objects;
import java.util.Set;

/**
 * OAuth 2.0 client configuration containing all necessary parameters for OAuth flows.
 *
 * @since 0.1.0
 */
public class OAuth2Config {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Set<String> scopes;
    private final String authorizationEndpoint;
    private final String tokenEndpoint;
    private final String revocationEndpoint;
    private final String userInfoEndpoint;

    private OAuth2Config(Builder builder) {
        this.clientId = Objects.requireNonNull(builder.clientId, "clientId must not be null");
        this.clientSecret = builder.clientSecret;
        this.redirectUri = builder.redirectUri;
        this.scopes = builder.scopes != null ? Set.copyOf(builder.scopes) : Set.of();
        this.authorizationEndpoint = builder.authorizationEndpoint;
        this.tokenEndpoint = builder.tokenEndpoint;
        this.revocationEndpoint = builder.revocationEndpoint;
        this.userInfoEndpoint = builder.userInfoEndpoint;
    }

    /**
     * Creates a new builder.
     *
     * @return the builder
     * @since 0.1.0
     */
    public static Builder builder() {
        return new Builder();
    }

    // Getters

    public String getClientId() { return clientId; }
    public String getClientSecret() { return clientSecret; }
    public String getRedirectUri() { return redirectUri; }
    public Set<String> getScopes() { return scopes; }
    public String getAuthorizationEndpoint() { return authorizationEndpoint; }
    public String getTokenEndpoint() { return tokenEndpoint; }
    public String getRevocationEndpoint() { return revocationEndpoint; }
    public String getUserInfoEndpoint() { return userInfoEndpoint; }

    /**
     * Builder for OAuth2Config.
     *
     * @since 0.1.0
     */
    public static class Builder {
        private String clientId;
        private String clientSecret;
        private String redirectUri;
        private Set<String> scopes;
        private String authorizationEndpoint;
        private String tokenEndpoint;
        private String revocationEndpoint;
        private String userInfoEndpoint;

        public Builder clientId(String clientId) { this.clientId = clientId; return this; }
        public Builder clientSecret(String clientSecret) { this.clientSecret = clientSecret; return this; }
        public Builder redirectUri(String redirectUri) { this.redirectUri = redirectUri; return this; }
        public Builder scopes(Set<String> scopes) { this.scopes = scopes; return this; }
        public Builder authorizationEndpoint(String url) { this.authorizationEndpoint = url; return this; }
        public Builder tokenEndpoint(String url) { this.tokenEndpoint = url; return this; }
        public Builder revocationEndpoint(String url) { this.revocationEndpoint = url; return this; }
        public Builder userInfoEndpoint(String url) { this.userInfoEndpoint = url; return this; }

        /**
         * Builds the configuration.
         *
         * @return the OAuth2Config
         * @since 0.1.0
         */
        public OAuth2Config build() {
            return new OAuth2Config(this);
        }
    }
}
