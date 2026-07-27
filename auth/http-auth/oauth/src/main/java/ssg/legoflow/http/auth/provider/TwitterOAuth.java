package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * Twitter (X) OAuth 2.0 provider configuration.
 *
 * @since 1.0.0
 */
public class TwitterOAuth extends OAuthProvider {

    /** Singleton instance. */
    public static final TwitterOAuth INSTANCE = new TwitterOAuth();

    public TwitterOAuth() { super("Twitter"); }

    @Override public String authorizationEndpoint() { return "https://twitter.com/i/oauth2/authorize"; }
    @Override public String tokenEndpoint() { return "https://api.twitter.com/2/oauth2/token"; }
    @Override public String userInfoEndpoint() { return "https://api.twitter.com/2/users/me"; }
    @Override public String revocationEndpoint() { return "https://api.twitter.com/2/oauth2/revoke"; }
    @Override public Set<String> defaultScopes() { return Set.of("tweet.read", "users.read"); }
}
