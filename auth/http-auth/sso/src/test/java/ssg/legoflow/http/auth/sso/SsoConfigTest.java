package ssg.legoflow.http.auth.sso;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class SsoConfigTest {

    @Test
    void testFullConstructor() {
        var config = new SsoConfig("example.com", "MY_SSO", Duration.ofHours(2),
                Set.of("https://app1.example.com", "https://app2.example.com"), true);
        assertThat(config.getDomain()).isEqualTo("example.com");
        assertThat(config.getCookieName()).isEqualTo("MY_SSO");
        assertThat(config.getSessionTimeout()).isEqualTo(Duration.ofHours(2));
        assertThat(config.getTrustedServices()).containsExactlyInAnyOrder(
                "https://app1.example.com", "https://app2.example.com");
        assertThat(config.isSecureCookies()).isTrue();
    }

    @Test
    void testDefaultCookieName() {
        var config = new SsoConfig("example.com", null, null, null, false);
        assertThat(config.getCookieName()).isEqualTo("LF_SSO");
    }

    @Test
    void testDefaultSessionTimeout() {
        var config = new SsoConfig("example.com", null, null, null, false);
        assertThat(config.getSessionTimeout()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void testDefaultTrustedServices() {
        var config = new SsoConfig("example.com", null, null, null, false);
        assertThat(config.getTrustedServices()).isEmpty();
    }

    @Test
    void testForDomain() {
        var config = SsoConfig.forDomain("test.com");
        assertThat(config.getDomain()).isEqualTo("test.com");
        assertThat(config.getCookieName()).isEqualTo("LF_SSO");
        assertThat(config.getSessionTimeout()).isEqualTo(Duration.ofHours(8));
        assertThat(config.getTrustedServices()).isEmpty();
        assertThat(config.isSecureCookies()).isTrue();
    }

    @Test
    void testNullDomainThrows() {
        assertThatThrownBy(() -> new SsoConfig(null, null, null, null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSecureCookiesFalse() {
        var config = new SsoConfig("example.com", null, null, null, false);
        assertThat(config.isSecureCookies()).isFalse();
    }

    @Test
    void testTrustedServicesImmutable() {
        var config = new SsoConfig("example.com", null, null, Set.of("svc1"), false);
        assertThatThrownBy(() -> config.getTrustedServices().add("svc2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
