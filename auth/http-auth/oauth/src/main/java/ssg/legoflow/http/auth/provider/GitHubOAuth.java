package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * GitHub OAuth 2.0 provider configuration.
 *
 * @since 0.1.0
 */
public class GitHubOAuth extends OAuthProvider {

    /** Singleton instance. */
    public static final GitHubOAuth INSTANCE = new GitHubOAuth();

    public GitHubOAuth() { super("GitHub"); }

    @Override public String authorizationEndpoint() { return "https://github.com/login/oauth/authorize"; }
    @Override public String tokenEndpoint() { return "https://github.com/login/oauth/access_token"; }
    @Override public String userInfoEndpoint() { return "https://api.github.com/user"; }
    @Override public Set<String> defaultScopes() { return Set.of("read:user", "user:email"); }
}
