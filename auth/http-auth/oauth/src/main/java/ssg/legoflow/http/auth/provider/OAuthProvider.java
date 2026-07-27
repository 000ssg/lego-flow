package ssg.legoflow.http.auth.provider;

import ssg.legoflow.http.auth.oauth2.OAuth2Config;

import java.util.Set;

/**
 * Abstract base for pre-configured OAuth provider templates. Each provider supplies
 * its authorization, token, and userinfo endpoint URLs so that consumers only need
 * to provide their client credentials.
 *
 * @since 1.0.0
 */
public abstract class OAuthProvider {

    private final String name;

    /**
     * Creates a provider.
     *
     * @param name the provider name
     * @since 1.0.0
     */
    protected OAuthProvider(String name) {
        this.name = name;
    }

    /**
     * Returns the provider name.
     *
     * @return the name
     * @since 1.0.0
     */
    public String getName() { return name; }

    /**
     * Returns the authorization endpoint URL.
     *
     * @return the URL
     * @since 1.0.0
     */
    public abstract String authorizationEndpoint();

    /**
     * Returns the token endpoint URL.
     *
     * @return the URL
     * @since 1.0.0
     */
    public abstract String tokenEndpoint();

    /**
     * Returns the user info endpoint URL.
     *
     * @return the URL, or null if not supported
     * @since 1.0.0
     */
    public abstract String userInfoEndpoint();

    /**
     * Returns the revocation endpoint URL.
     *
     * @return the URL, or null if not supported
     * @since 1.0.0
     */
    public String revocationEndpoint() { return null; }

    /**
     * Returns the default scopes for this provider.
     *
     * @return the default scopes
     * @since 1.0.0
     */
    public abstract Set<String> defaultScopes();

    /**
     * Builds an OAuth2Config for this provider.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret
     * @param redirectUri  the redirect URI
     * @return the configuration
     * @since 1.0.0
     */
    public OAuth2Config buildConfig(String clientId, String clientSecret, String redirectUri) {
        var builder = OAuth2Config.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .authorizationEndpoint(authorizationEndpoint())
                .tokenEndpoint(tokenEndpoint())
                .scopes(defaultScopes());
        if (userInfoEndpoint() != null) builder.userInfoEndpoint(userInfoEndpoint());
        if (revocationEndpoint() != null) builder.revocationEndpoint(revocationEndpoint());
        return builder.build();
    }

    /**
     * Builds an OAuth2Config with additional scopes.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret
     * @param redirectUri  the redirect URI
     * @param scopes       the scopes (overrides defaults)
     * @return the configuration
     * @since 1.0.0
     */
    public OAuth2Config buildConfig(String clientId, String clientSecret, String redirectUri,
                                     Set<String> scopes) {
        return OAuth2Config.builder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .redirectUri(redirectUri)
                .authorizationEndpoint(authorizationEndpoint())
                .tokenEndpoint(tokenEndpoint())
                .userInfoEndpoint(userInfoEndpoint())
                .revocationEndpoint(revocationEndpoint())
                .scopes(scopes)
                .build();
    }
}
