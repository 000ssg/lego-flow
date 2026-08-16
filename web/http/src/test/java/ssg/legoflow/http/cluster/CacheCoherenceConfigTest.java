package ssg.legoflow.http.cluster;

import ssg.legoflow.http.core.HttpMethod;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link CacheCoherenceConfig} builder and defaults.
 */
class CacheCoherenceConfigTest {

    @Test
    void builder_defaults() {
        var config = CacheCoherenceConfig.builder().build();

        assertThat(config.invalidationMethods())
                .contains(HttpMethod.PUT, HttpMethod.POST, HttpMethod.DELETE, HttpMethod.PATCH);
        assertThat(config.invalidationMethods())
                .doesNotContain(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
        assertThat(config.invalidationScope())
                .isEqualTo(CacheCoherenceConfig.InvalidationScope.PREFIX);
        assertThat(config.propagationTimeout())
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    void builder_custom_methods() {
        var methods = Set.of(HttpMethod.PUT, HttpMethod.DELETE);
        var config = CacheCoherenceConfig.builder()
                .invalidationMethods(methods)
                .build();

        assertThat(config.invalidationMethods()).containsExactlyInAnyOrder(HttpMethod.PUT, HttpMethod.DELETE);
    }

    @Test
    void builder_custom_scope_path() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .build();

        assertThat(config.invalidationScope())
                .isEqualTo(CacheCoherenceConfig.InvalidationScope.PATH);
    }

    @Test
    void builder_custom_scope_all() {
        var config = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .build();

        assertThat(config.invalidationScope())
                .isEqualTo(CacheCoherenceConfig.InvalidationScope.ALL);
    }

    @Test
    void builder_custom_timeout() {
        var timeout = Duration.ofMillis(2500);
        var config = CacheCoherenceConfig.builder()
                .propagationTimeout(timeout)
                .build();

        assertThat(config.propagationTimeout()).isEqualTo(timeout);
    }

    @Test
    void builder_all_custom() {
        var methods = Set.of(HttpMethod.PUT);
        var config = CacheCoherenceConfig.builder()
                .invalidationMethods(methods)
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .propagationTimeout(Duration.ofSeconds(1))
                .build();

        assertThat(config.invalidationMethods()).containsExactly(HttpMethod.PUT);
        assertThat(config.invalidationScope()).isEqualTo(CacheCoherenceConfig.InvalidationScope.ALL);
        assertThat(config.propagationTimeout()).isEqualTo(Duration.ofSeconds(1));
    }

    @Test
    void builder_null_methods_throws() {
        assertThatThrownBy(() -> CacheCoherenceConfig.builder()
                .invalidationMethods(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_null_scope_throws() {
        assertThatThrownBy(() -> CacheCoherenceConfig.builder()
                .invalidationScope(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builder_null_timeout_throws() {
        assertThatThrownBy(() -> CacheCoherenceConfig.builder()
                .propagationTimeout(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void scope_enum_values() {
        var values = CacheCoherenceConfig.InvalidationScope.values();
        assertThat(values).containsExactly(
                CacheCoherenceConfig.InvalidationScope.PATH,
                CacheCoherenceConfig.InvalidationScope.PREFIX,
                CacheCoherenceConfig.InvalidationScope.ALL
        );
    }

    @Test
    void record_equality() {
        var methods = Set.of(HttpMethod.PUT, HttpMethod.DELETE);
        var config1 = CacheCoherenceConfig.builder()
                .invalidationMethods(methods)
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .propagationTimeout(Duration.ofSeconds(3))
                .build();

        var config2 = CacheCoherenceConfig.builder()
                .invalidationMethods(methods)
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.PATH)
                .propagationTimeout(Duration.ofSeconds(3))
                .build();

        assertThat(config1).isEqualTo(config2);
        assertThat(config1.hashCode()).isEqualTo(config2.hashCode());
    }

    @Test
    void record_inequality() {
        var config1 = CacheCoherenceConfig.builder().build();
        var config2 = CacheCoherenceConfig.builder()
                .invalidationScope(CacheCoherenceConfig.InvalidationScope.ALL)
                .build();

        assertThat(config1).isNotEqualTo(config2);
    }
}
