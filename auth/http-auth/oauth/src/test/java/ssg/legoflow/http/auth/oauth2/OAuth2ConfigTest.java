package ssg.legoflow.http.auth.oauth2;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class OAuth2ConfigTest {

    @Test
    void testBuildConfig() {
        var config = OAuth2Config.builder()
                .clientId("client1")
                .clientSecret("secret")
                .redirectUri("http://localhost/callback")
                .authorizationEndpoint("https://auth.example.com/authorize")
                .tokenEndpoint("https://auth.example.com/token")
                .scopes(Set.of("openid", "email"))
                .build();

        assertThat(config.getClientId()).isEqualTo("client1");
        assertThat(config.getClientSecret()).isEqualTo("secret");
        assertThat(config.getRedirectUri()).isEqualTo("http://localhost/callback");
        assertThat(config.getScopes()).containsExactlyInAnyOrder("openid", "email");
    }

    @Test
    void testNullClientIdThrows() {
        assertThatThrownBy(() -> OAuth2Config.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testOptionalFieldsNull() {
        var config = OAuth2Config.builder().clientId("c1").build();
        assertThat(config.getClientSecret()).isNull();
        assertThat(config.getRedirectUri()).isNull();
        assertThat(config.getScopes()).isEmpty();
        assertThat(config.getRevocationEndpoint()).isNull();
        assertThat(config.getUserInfoEndpoint()).isNull();
    }
}
