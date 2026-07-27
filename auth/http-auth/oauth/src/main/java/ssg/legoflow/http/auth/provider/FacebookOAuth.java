package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * Facebook Login OAuth 2.0 provider configuration.
 *
 * @since 1.0.0
 */
public class FacebookOAuth extends OAuthProvider {

    /** Singleton instance. */
    public static final FacebookOAuth INSTANCE = new FacebookOAuth();

    public FacebookOAuth() { super("Facebook"); }

    @Override public String authorizationEndpoint() { return "https://www.facebook.com/v18.0/dialog/oauth"; }
    @Override public String tokenEndpoint() { return "https://graph.facebook.com/v18.0/oauth/access_token"; }
    @Override public String userInfoEndpoint() { return "https://graph.facebook.com/me?fields=id,name,email"; }
    @Override public Set<String> defaultScopes() { return Set.of("email", "public_profile"); }
}
