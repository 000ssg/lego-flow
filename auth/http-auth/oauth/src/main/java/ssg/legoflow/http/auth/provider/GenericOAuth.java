package ssg.legoflow.http.auth.provider;

import java.util.Objects;
import java.util.Set;

/**
 * Generic OAuth 2.0 provider configurable for any OAuth 2.0/OpenID Connect server.
 *
 * @since 1.0.0
 */
public class GenericOAuth extends OAuthProvider {

    private final String authorizationUrl;
    private final String tokenUrl;
    private final String userInfoUrl;
    private final String revocationUrl;
    private final Set<String> scopes;

    /**
     * Creates a generic OAuth provider.
     *
     * @param name             the provider name
     * @param authorizationUrl the authorization endpoint URL
     * @param tokenUrl         the token endpoint URL
     * @param userInfoUrl      the user info endpoint URL (may be null)
     * @param revocationUrl    the revocation endpoint URL (may be null)
     * @param scopes           the default scopes
     * @since 1.0.0
     */
    public GenericOAuth(String name, String authorizationUrl, String tokenUrl,
                        String userInfoUrl, String revocationUrl, Set<String> scopes) {
        super(name);
        this.authorizationUrl = Objects.requireNonNull(authorizationUrl);
        this.tokenUrl = Objects.requireNonNull(tokenUrl);
        this.userInfoUrl = userInfoUrl;
        this.revocationUrl = revocationUrl;
        this.scopes = scopes != null ? Set.copyOf(scopes) : Set.of();
    }

    @Override public String authorizationEndpoint() { return authorizationUrl; }
    @Override public String tokenEndpoint() { return tokenUrl; }
    @Override public String userInfoEndpoint() { return userInfoUrl; }
    @Override public String revocationEndpoint() { return revocationUrl; }
    @Override public Set<String> defaultScopes() { return scopes; }
}
