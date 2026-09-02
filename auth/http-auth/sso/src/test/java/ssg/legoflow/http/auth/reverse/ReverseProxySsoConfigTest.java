package ssg.legoflow.http.auth.reverse;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;
class ReverseProxySsoConfigTest {

    @Test
    void testFullConstructor() {
        var config = new ReverseProxySsoConfig("x-user", "x-roles", "x-email", "x-name",
                Set.of("10.0.0.1", "10.0.0.2"), true);
        assertThat(config.getUserHeader()).isEqualTo("x-user");
        assertThat(config.getRolesHeader()).isEqualTo("x-roles");
        assertThat(config.getEmailHeader()).isEqualTo("x-email");
        assertThat(config.getNameHeader()).isEqualTo("x-name");
        assertThat(config.getTrustedProxies()).containsExactlyInAnyOrder("10.0.0.1", "10.0.0.2");
        assertThat(config.isRequireProxy()).isTrue();
    }

    @Test
    void testDefaultHeaders() {
        var config = new ReverseProxySsoConfig(null, null, null, null, null, false);
        assertThat(config.getUserHeader()).isEqualTo("x-forwarded-user");
        assertThat(config.getRolesHeader()).isEqualTo("x-forwarded-roles");
        assertThat(config.getEmailHeader()).isEqualTo("x-forwarded-email");
        assertThat(config.getNameHeader()).isEqualTo("x-forwarded-name");
    }

    @Test
    void testDefaultTrustedProxies() {
        var config = new ReverseProxySsoConfig(null, null, null, null, null, false);
        assertThat(config.getTrustedProxies()).isEmpty();
    }

    @Test
    void testDefaults() {
        var config = ReverseProxySsoConfig.defaults();
        assertThat(config.getUserHeader()).isEqualTo("x-forwarded-user");
        assertThat(config.getRolesHeader()).isEqualTo("x-forwarded-roles");
        assertThat(config.getEmailHeader()).isEqualTo("x-forwarded-email");
        assertThat(config.getNameHeader()).isEqualTo("x-forwarded-name");
        assertThat(config.getTrustedProxies()).isEmpty();
        assertThat(config.isRequireProxy()).isFalse();
    }

    @Test
    void testRequireProxyFalse() {
        var config = ReverseProxySsoConfig.defaults();
        assertThat(config.isRequireProxy()).isFalse();
    }

    @Test
    void testTrustedProxiesImmutable() {
        var config = new ReverseProxySsoConfig(null, null, null, null, Set.of("10.0.0.1"), false);
        assertThatThrownBy(() -> config.getTrustedProxies().add("10.0.0.2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
