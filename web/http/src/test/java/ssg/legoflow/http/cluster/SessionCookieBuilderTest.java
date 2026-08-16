package ssg.legoflow.http.cluster;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionCookieBuilderTest {

    @Test
    void build_basic_cookie() {
        String cookie = new SessionCookieBuilder("SESSION")
                .path("/")
                .maxAge(Duration.ofSeconds(3600))
                .build("node-1");

        assertThat(cookie).startsWith("SESSION=node-1");
        assertThat(cookie).contains("; Path=/");
        assertThat(cookie).contains("; Max-Age=3600");
        assertThat(cookie).contains("; Expires=");
        assertThat(cookie).contains("; SameSite=Lax");
    }

    @Test
    void build_session_cookie_no_expiry() {
        String cookie = new SessionCookieBuilder("SESSION")
                .path("/")
                .maxAge(Duration.ofSeconds(-1))
                .build("node-2");

        assertThat(cookie).startsWith("SESSION=node-2");
        assertThat(cookie).contains("; Path=/");
        assertThat(cookie).doesNotContain("Max-Age");
        assertThat(cookie).doesNotContain("Expires");
    }

    @Test
    void build_secure_and_httponly() {
        String cookie = new SessionCookieBuilder("SESSION")
                .secure(true)
                .httpOnly(true)
                .maxAge(Duration.ofMinutes(10))
                .build("node-A");

        assertThat(cookie).contains("; Secure");
        assertThat(cookie).contains("; HttpOnly");
    }

    @Test
    void build_not_secure_not_httponly() {
        String cookie = new SessionCookieBuilder("SESSION")
                .secure(false)
                .httpOnly(false)
                .maxAge(Duration.ofMinutes(10))
                .build("node-A");

        assertThat(cookie).doesNotContain("Secure");
        assertThat(cookie).doesNotContain("HttpOnly");
    }

    @Test
    void build_custom_sameSite() {
        String cookie = new SessionCookieBuilder("SESSION")
                .sameSite("Strict")
                .maxAge(Duration.ofSeconds(60))
                .build("node-1");

        assertThat(cookie).contains("; SameSite=Strict");
    }

    @Test
    void build_custom_path() {
        String cookie = new SessionCookieBuilder("SESSION")
                .path("/api")
                .maxAge(Duration.ofSeconds(60))
                .build("node-1");

        assertThat(cookie).contains("; Path=/api");
    }

    @Test
    void from_config() {
        SessionAffinityConfig config = SessionAffinityConfig.builder()
                .cookieName("app-session")
                .maxAge(Duration.ofHours(2))
                .secure(true)
                .httpOnly(true)
                .path("/app")
                .build();

        SessionCookieBuilder builder = SessionCookieBuilder.fromConfig(config);
        String cookie = builder.build("node-42");

        assertThat(cookie).startsWith("app-session=node-42");
        assertThat(cookie).contains("; Max-Age=7200");
        assertThat(cookie).contains("; Path=/app");
        assertThat(cookie).contains("; Secure");
        assertThat(cookie).contains("; HttpOnly");
    }

    @Test
    void null_cookieName_throws() {
        assertThatThrownBy(() -> new SessionCookieBuilder(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void null_nodeId_throws() {
        assertThatThrownBy(() -> new SessionCookieBuilder("SESSION").build(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void maxAgeSeconds() {
        String cookie = new SessionCookieBuilder("TOKEN")
                .maxAgeSeconds(1800)
                .build("node-x");

        assertThat(cookie).contains("; Max-Age=1800");
    }
}
