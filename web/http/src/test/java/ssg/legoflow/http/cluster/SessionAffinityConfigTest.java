package ssg.legoflow.http.cluster;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class SessionAffinityConfigTest {

    @Test
    void builder_with_defaults() {
        SessionAffinityConfig config = SessionAffinityConfig.builder().build();
        assertThat(config.cookieName()).isEqualTo("X-Session-Node");
        assertThat(config.maxAge()).isEqualTo(Duration.ofHours(1));
        assertThat(config.secure()).isFalse();
        assertThat(config.httpOnly()).isTrue();
        assertThat(config.path()).isEqualTo("/");
        assertThat(config.fallback()).isEqualTo(SessionAffinityConfig.FallbackStrategy.REHASH);
    }

    @Test
    void builder_with_custom_values() {
        SessionAffinityConfig config = SessionAffinityConfig.builder()
                .cookieName("my-session")
                .maxAge(Duration.ofMinutes(30))
                .secure(true)
                .httpOnly(false)
                .path("/app")
                .fallback(SessionAffinityConfig.FallbackStrategy.REDIRECT)
                .build();

        assertThat(config.cookieName()).isEqualTo("my-session");
        assertThat(config.maxAge()).isEqualTo(Duration.ofMinutes(30));
        assertThat(config.secure()).isTrue();
        assertThat(config.httpOnly()).isFalse();
        assertThat(config.path()).isEqualTo("/app");
        assertThat(config.fallback()).isEqualTo(SessionAffinityConfig.FallbackStrategy.REDIRECT);
    }

    @Test
    void null_values_thrown() {
        assertThatThrownBy(() -> SessionAffinityConfig.builder().cookieName(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SessionAffinityConfig.builder().maxAge(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SessionAffinityConfig.builder().path(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SessionAffinityConfig.builder().fallback(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fallback_strategy_values() {
        SessionAffinityConfig config1 = SessionAffinityConfig.builder()
                .fallback(SessionAffinityConfig.FallbackStrategy.REHASH).build();
        SessionAffinityConfig config2 = SessionAffinityConfig.builder()
                .fallback(SessionAffinityConfig.FallbackStrategy.REDIRECT).build();
        SessionAffinityConfig config3 = SessionAffinityConfig.builder()
                .fallback(SessionAffinityConfig.FallbackStrategy.ERROR).build();

        assertThat(config1.fallback()).isEqualTo(SessionAffinityConfig.FallbackStrategy.REHASH);
        assertThat(config2.fallback()).isEqualTo(SessionAffinityConfig.FallbackStrategy.REDIRECT);
        assertThat(config3.fallback()).isEqualTo(SessionAffinityConfig.FallbackStrategy.ERROR);
    }

    @Test
    void record_equals() {
        var c1 = SessionAffinityConfig.builder()
                .cookieName("s1").maxAge(Duration.ofSeconds(60))
                .secure(true).httpOnly(true).path("/").fallback(SessionAffinityConfig.FallbackStrategy.REHASH)
                .build();
        var c2 = SessionAffinityConfig.builder()
                .cookieName("s1").maxAge(Duration.ofSeconds(60))
                .secure(true).httpOnly(true).path("/").fallback(SessionAffinityConfig.FallbackStrategy.REHASH)
                .build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }
}
