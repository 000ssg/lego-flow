package ssg.legoflow.auth.gssapi;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link GssConfig} builder, defaults, and system property application.
 */
class GssConfigTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("java.security.krb5.realm");
        System.clearProperty("java.security.krb5.kdc");
        System.clearProperty("javax.security.auth.useSubjectCredsOnly");
    }

    @Test
    void testBuilderSetsRealm() {
        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("host/server.example.com@EXAMPLE.COM")
                .build();
        assertThat(config.realm()).isEqualTo("EXAMPLE.COM");
    }

    @Test
    void testBuilderSetsKdc() {
        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("host/server.example.com@EXAMPLE.COM")
                .build();
        assertThat(config.kdc()).isEqualTo("kdc.example.com");
    }

    @Test
    void testBuilderSetsServicePrincipal() {
        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc.example.com")
                .servicePrincipal("HTTP/web.example.com@EXAMPLE.COM")
                .build();
        assertThat(config.servicePrincipal()).isEqualTo("HTTP/web.example.com@EXAMPLE.COM");
    }

    @Test
    void testKeytabPathDefaultNull() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .build();
        assertThat(config.keytabPath()).isNull();
    }

    @Test
    void testBuilderSetsKeytabPath() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .keytabPath("/etc/krb5.keytab")
                .build();
        assertThat(config.keytabPath()).isEqualTo("/etc/krb5.keytab");
    }

    @Test
    void testUseSubjectCredsOnlyDefaultTrue() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .build();
        assertThat(config.useSubjectCredsOnly()).isTrue();
    }

    @Test
    void testUseSubjectCredsOnlySetFalse() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .useSubjectCredsOnly(false)
                .build();
        assertThat(config.useSubjectCredsOnly()).isFalse();
    }

    @Test
    void testNullRealmThrows() {
        assertThatThrownBy(() ->
                GssConfig.builder()
                        .kdc("k")
                        .servicePrincipal("s")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("realm");
    }

    @Test
    void testNullKdcThrows() {
        assertThatThrownBy(() ->
                GssConfig.builder()
                        .realm("R")
                        .servicePrincipal("s")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("kdc");
    }

    @Test
    void testNullServicePrincipalThrows() {
        assertThatThrownBy(() ->
                GssConfig.builder()
                        .realm("R")
                        .kdc("k")
                        .build()
        ).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("servicePrincipal");
    }

    @Test
    void testApplyAsSystemProperties() {
        GssConfig config = GssConfig.builder()
                .realm("TEST.REALM")
                .kdc("kdc.test.realm")
                .servicePrincipal("host/test@TEST.REALM")
                .build();
        config.applyAsSystemProperties();

        assertThat(System.getProperty("java.security.krb5.realm")).isEqualTo("TEST.REALM");
        assertThat(System.getProperty("java.security.krb5.kdc")).isEqualTo("kdc.test.realm");
        assertThat(System.getProperty("javax.security.auth.useSubjectCredsOnly")).isEqualTo("true");
    }

    @Test
    void testApplyAsSystemPropertiesSubjectCredsFalse() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("s")
                .useSubjectCredsOnly(false)
                .build();
        config.applyAsSystemProperties();

        assertThat(System.getProperty("javax.security.auth.useSubjectCredsOnly")).isEqualTo("false");
    }

    @Test
    void testToStringContainsRealm() {
        GssConfig config = GssConfig.builder()
                .realm("EXAMPLE.COM")
                .kdc("kdc")
                .servicePrincipal("sp")
                .build();
        assertThat(config.toString()).contains("EXAMPLE.COM");
    }

    @Test
    void testToStringContainsKdc() {
        GssConfig config = GssConfig.builder()
                .realm("R")
                .kdc("kdc.host")
                .servicePrincipal("sp")
                .build();
        assertThat(config.toString()).contains("kdc.host");
    }

    @Test
    void testImmutabilityRealmCannotChangeAfterBuild() {
        GssConfig.Builder builder = GssConfig.builder()
                .realm("R1")
                .kdc("k")
                .servicePrincipal("s");
        GssConfig config1 = builder.build();
        builder.realm("R2");
        GssConfig config2 = builder.build();

        assertThat(config1.realm()).isEqualTo("R1");
        assertThat(config2.realm()).isEqualTo("R2");
    }
}
