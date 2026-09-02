package ssg.legoflow.http.auth.provider;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class OAuthProviderTest {

    // --- Google ---

    @Test
    void testGoogleName() {
        assertThat(GoogleOAuth.INSTANCE.getName()).isEqualTo("Google");
    }

    @Test
    void testGoogleEndpoints() {
        assertThat(GoogleOAuth.INSTANCE.authorizationEndpoint()).contains("google.com");
        assertThat(GoogleOAuth.INSTANCE.tokenEndpoint()).contains("googleapis.com");
        assertThat(GoogleOAuth.INSTANCE.userInfoEndpoint()).contains("googleapis.com");
        assertThat(GoogleOAuth.INSTANCE.revocationEndpoint()).contains("googleapis.com");
    }

    @Test
    void testGoogleDefaultScopes() {
        assertThat(GoogleOAuth.INSTANCE.defaultScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
    }

    @Test
    void testGoogleBuildConfig() {
        var config = GoogleOAuth.INSTANCE.buildConfig("myClientId", "mySecret", "http://localhost/callback");
        assertThat(config.getClientId()).isEqualTo("myClientId");
        assertThat(config.getClientSecret()).isEqualTo("mySecret");
        assertThat(config.getRedirectUri()).isEqualTo("http://localhost/callback");
        assertThat(config.getAuthorizationEndpoint()).contains("google.com");
        assertThat(config.getTokenEndpoint()).contains("googleapis.com");
    }

    // --- GitHub ---

    @Test
    void testGitHubName() {
        assertThat(GitHubOAuth.INSTANCE.getName()).isEqualTo("GitHub");
    }

    @Test
    void testGitHubEndpoints() {
        assertThat(GitHubOAuth.INSTANCE.authorizationEndpoint()).contains("github.com");
        assertThat(GitHubOAuth.INSTANCE.tokenEndpoint()).contains("github.com");
        assertThat(GitHubOAuth.INSTANCE.userInfoEndpoint()).contains("api.github.com");
    }

    @Test
    void testGitHubDefaultScopes() {
        assertThat(GitHubOAuth.INSTANCE.defaultScopes()).contains("read:user");
    }

    @Test
    void testGitHubNoRevocationEndpoint() {
        assertThat(GitHubOAuth.INSTANCE.revocationEndpoint()).isNull();
    }

    // --- Microsoft ---

    @Test
    void testMicrosoftName() {
        assertThat(MicrosoftOAuth.INSTANCE.getName()).isEqualTo("Microsoft");
    }

    @Test
    void testMicrosoftCommonTenant() {
        assertThat(MicrosoftOAuth.INSTANCE.getTenant()).isEqualTo("common");
        assertThat(MicrosoftOAuth.INSTANCE.authorizationEndpoint()).contains("common");
    }

    @Test
    void testMicrosoftCustomTenant() {
        var ms = new MicrosoftOAuth("my-tenant-id");
        assertThat(ms.authorizationEndpoint()).contains("my-tenant-id");
        assertThat(ms.tokenEndpoint()).contains("my-tenant-id");
    }

    @Test
    void testMicrosoftEndpoints() {
        assertThat(MicrosoftOAuth.INSTANCE.tokenEndpoint()).contains("microsoftonline.com");
        assertThat(MicrosoftOAuth.INSTANCE.userInfoEndpoint()).contains("graph.microsoft.com");
    }

    @Test
    void testMicrosoftDefaultScopes() {
        assertThat(MicrosoftOAuth.INSTANCE.defaultScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
    }

    // --- Facebook ---

    @Test
    void testFacebookName() {
        assertThat(FacebookOAuth.INSTANCE.getName()).isEqualTo("Facebook");
    }

    @Test
    void testFacebookEndpoints() {
        assertThat(FacebookOAuth.INSTANCE.authorizationEndpoint()).contains("facebook.com");
        assertThat(FacebookOAuth.INSTANCE.tokenEndpoint()).contains("facebook.com");
        assertThat(FacebookOAuth.INSTANCE.userInfoEndpoint()).contains("facebook.com");
    }

    @Test
    void testFacebookDefaultScopes() {
        assertThat(FacebookOAuth.INSTANCE.defaultScopes()).contains("email");
    }

    // --- Twitter ---

    @Test
    void testTwitterName() {
        assertThat(TwitterOAuth.INSTANCE.getName()).isEqualTo("Twitter");
    }

    @Test
    void testTwitterEndpoints() {
        assertThat(TwitterOAuth.INSTANCE.authorizationEndpoint()).contains("twitter.com");
        assertThat(TwitterOAuth.INSTANCE.tokenEndpoint()).contains("twitter.com");
        assertThat(TwitterOAuth.INSTANCE.userInfoEndpoint()).contains("twitter.com");
        assertThat(TwitterOAuth.INSTANCE.revocationEndpoint()).contains("twitter.com");
    }

    @Test
    void testTwitterDefaultScopes() {
        assertThat(TwitterOAuth.INSTANCE.defaultScopes()).contains("tweet.read");
    }

    // --- Apple ---

    @Test
    void testAppleName() {
        assertThat(AppleOAuth.INSTANCE.getName()).isEqualTo("Apple");
    }

    @Test
    void testAppleEndpoints() {
        assertThat(AppleOAuth.INSTANCE.authorizationEndpoint()).contains("apple.com");
        assertThat(AppleOAuth.INSTANCE.tokenEndpoint()).contains("apple.com");
        assertThat(AppleOAuth.INSTANCE.userInfoEndpoint()).isNull();
        assertThat(AppleOAuth.INSTANCE.revocationEndpoint()).contains("apple.com");
    }

    @Test
    void testAppleDefaultScopes() {
        assertThat(AppleOAuth.INSTANCE.defaultScopes()).containsExactlyInAnyOrder("openid", "email", "name");
    }

    // --- Generic ---

    @Test
    void testGenericProvider() {
        var generic = new GenericOAuth("Custom", "https://custom.com/auth",
                "https://custom.com/token", "https://custom.com/userinfo",
                "https://custom.com/revoke", Set.of("openid"));
        assertThat(generic.getName()).isEqualTo("Custom");
        assertThat(generic.authorizationEndpoint()).isEqualTo("https://custom.com/auth");
        assertThat(generic.tokenEndpoint()).isEqualTo("https://custom.com/token");
        assertThat(generic.userInfoEndpoint()).isEqualTo("https://custom.com/userinfo");
        assertThat(generic.revocationEndpoint()).isEqualTo("https://custom.com/revoke");
        assertThat(generic.defaultScopes()).containsExactly("openid");
    }

    @Test
    void testGenericProviderMinimal() {
        var generic = new GenericOAuth("Min", "https://auth.com/a", "https://auth.com/t",
                null, null, null);
        assertThat(generic.userInfoEndpoint()).isNull();
        assertThat(generic.revocationEndpoint()).isNull();
        assertThat(generic.defaultScopes()).isEmpty();
    }

    @Test
    void testGenericProviderNullAuthUrlThrows() {
        assertThatThrownBy(() -> new GenericOAuth("X", null, "https://t.com", null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGenericProviderNullTokenUrlThrows() {
        assertThatThrownBy(() -> new GenericOAuth("X", "https://a.com", null, null, null, null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- buildConfig ---

    @Test
    void testBuildConfigWithDefaultScopes() {
        var config = GoogleOAuth.INSTANCE.buildConfig("cid", "csecret", "http://redir");
        assertThat(config.getScopes()).containsExactlyInAnyOrder("openid", "email", "profile");
    }

    @Test
    void testBuildConfigWithCustomScopes() {
        var config = GoogleOAuth.INSTANCE.buildConfig("cid", "csecret", "http://redir",
                Set.of("custom_scope"));
        assertThat(config.getScopes()).containsExactly("custom_scope");
    }

    @Test
    void testBuildConfigSetsUserInfoEndpoint() {
        var config = GoogleOAuth.INSTANCE.buildConfig("cid", "csecret", "http://redir");
        assertThat(config.getUserInfoEndpoint()).isNotNull();
    }

    @Test
    void testBuildConfigSetsRevocationEndpoint() {
        var config = GoogleOAuth.INSTANCE.buildConfig("cid", "csecret", "http://redir");
        assertThat(config.getRevocationEndpoint()).isNotNull();
    }

    @Test
    void testAppleBuildConfigNoUserInfo() {
        var config = AppleOAuth.INSTANCE.buildConfig("cid", "csecret", "http://redir");
        // Apple has null userInfoEndpoint, so buildConfig should handle it
        assertThat(config).isNotNull();
    }
}
