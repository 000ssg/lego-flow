package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * Google OAuth 2.0 + OpenID Connect provider configuration.
 *
 * @since 1.0.0
 */
public class GoogleOAuth extends OAuthProvider {

    /** Singleton instance. */
    public static final GoogleOAuth INSTANCE = new GoogleOAuth();

    /**
     * Creates a Google OAuth provider.
     *
     * @since 1.0.0
     */
    public GoogleOAuth() {
        super("Google");
    }

    @Override public String authorizationEndpoint() { return "https://accounts.google.com/o/oauth2/v2/auth"; }
    @Override public String tokenEndpoint() { return "https://oauth2.googleapis.com/token"; }
    @Override public String userInfoEndpoint() { return "https://openidconnect.googleapis.com/v1/userinfo"; }
    @Override public String revocationEndpoint() { return "https://oauth2.googleapis.com/revoke"; }
    @Override public Set<String> defaultScopes() { return Set.of("openid", "email", "profile"); }
}
