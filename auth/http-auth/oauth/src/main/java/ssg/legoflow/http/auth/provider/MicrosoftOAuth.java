package ssg.legoflow.http.auth.provider;

import java.util.Set;

/**
 * Microsoft Identity Platform (Azure AD) OAuth 2.0 + OpenID Connect provider.
 *
 * @since 1.0.0
 */
public class MicrosoftOAuth extends OAuthProvider {

    /** Singleton instance (common tenant). */
    public static final MicrosoftOAuth INSTANCE = new MicrosoftOAuth("common");

    private final String tenant;

    public MicrosoftOAuth(String tenant) {
        super("Microsoft");
        this.tenant = tenant;
    }

    @Override public String authorizationEndpoint() { return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/authorize"; }
    @Override public String tokenEndpoint() { return "https://login.microsoftonline.com/" + tenant + "/oauth2/v2.0/token"; }
    @Override public String userInfoEndpoint() { return "https://graph.microsoft.com/oidc/userinfo"; }
    @Override public Set<String> defaultScopes() { return Set.of("openid", "email", "profile"); }

    /** Returns the tenant ID. */
    public String getTenant() { return tenant; }
}
