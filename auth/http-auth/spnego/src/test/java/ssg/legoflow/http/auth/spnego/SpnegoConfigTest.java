package ssg.legoflow.http.auth.spnego;

import ssg.legoflow.auth.gssapi.GssConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SpnegoConfig}.
 */
class SpnegoConfigTest {

    private GssConfig createGssConfig() {
        return GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/server.example.com@EXAMPLE.COM")
                .build();
    }

    @Test
    void testBuilderWithDefaults() {
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(createGssConfig())
                .build();
        assertThat(config.gssConfig()).isNotNull();
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test
    void testBuilderStripRealmFalse() {
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(createGssConfig())
                .stripRealmFromPrincipal(false)
                .build();
        assertThat(config.stripRealmFromPrincipal()).isFalse();
    }

    @Test
    void testBuilderStripRealmTrue() {
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(createGssConfig())
                .stripRealmFromPrincipal(true)
                .build();
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test
    void testOfFactory() {
        GssConfig gss = createGssConfig();
        SpnegoConfig config = SpnegoConfig.of(gss);
        assertThat(config.gssConfig()).isSameAs(gss);
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test
    void testNullGssConfigThrows() {
        assertThatThrownBy(() -> SpnegoConfig.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("gssConfig");
    }

    @Test
    void testNullGssConfigInOfThrows() {
        assertThatThrownBy(() -> SpnegoConfig.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGssConfigAccessor() {
        GssConfig gss = createGssConfig();
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(gss)
                .build();
        assertThat(config.gssConfig().realm()).isEqualTo("EXAMPLE.COM");
        assertThat(config.gssConfig().kdc()).isEqualTo("kdc.example.com");
        assertThat(config.gssConfig().servicePrincipal()).isEqualTo("HTTP/server.example.com@EXAMPLE.COM");
    }

    @Test
    void testToStringContainsFields() {
        SpnegoConfig config = SpnegoConfig.of(createGssConfig());
        String str = config.toString();
        assertThat(str).contains("SpnegoConfig");
        assertThat(str).contains("stripRealmFromPrincipal=true");
    }

    @Test
    void testDefaultStripRealmIsTrue() {
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(createGssConfig())
                .build();
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test
    void testBuilderFluentChaining() {
        GssConfig gss = createGssConfig();
        SpnegoConfig config = SpnegoConfig.builder()
                .gssConfig(gss)
                .stripRealmFromPrincipal(false)
                .build();
        assertThat(config.gssConfig()).isSameAs(gss);
        assertThat(config.stripRealmFromPrincipal()).isFalse();
    }
}
