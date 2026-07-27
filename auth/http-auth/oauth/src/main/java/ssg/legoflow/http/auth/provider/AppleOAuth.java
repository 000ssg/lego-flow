package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * Sign in with Apple OAuth 2.0 provider configuration.
 *
 * @since 1.0.0
 */
public class AppleOAuth extends OAuthProvider {

    /** Singleton instance. */
    public static final AppleOAuth INSTANCE = new AppleOAuth();

    public AppleOAuth() { super("Apple"); }

    @Override public String authorizationEndpoint() { return "https://appleid.apple.com/auth/authorize"; }
    @Override public String tokenEndpoint() { return "https://appleid.apple.com/auth/token"; }
    @Override public String userInfoEndpoint() { return null; /* Apple returns user info in the ID token */ }
    @Override public String revocationEndpoint() { return "https://appleid.apple.com/auth/revoke"; }
    @Override public Set<String> defaultScopes() { return Set.of("openid", "email", "name"); }
}
