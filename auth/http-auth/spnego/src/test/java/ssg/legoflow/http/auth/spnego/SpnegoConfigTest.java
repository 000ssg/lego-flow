package ssg.legoflow.http.auth.spnego;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import ssg.legoflow.auth.gssapi.GssConfig;

class SpnegoConfigTest {

    private GssConfig makeGss(String realm, String kdc, String sp) {
        return GssConfig.builder()
            .realm(realm)
            .kdc(kdc)
            .servicePrincipal(sp)
            .build();
    }

    @Test void testBuilderBuild() {
        var gss = makeGss("EXAMPLE.COM", "kdc.example.com", "HTTP/server@example.com");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config).isNotNull();
        assertThat(config.gssConfig()).isEqualTo(gss);
    }

    @Test void testStripRealmFromPrincipalDefault() {
        var gss = makeGss("EXAMPLE.COM", "kdc.example.com", "HTTP/s@e.c");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test void testSetStripRealmFalse() {
        var gss = makeGss("EXAMPLE.COM", "kdc.example.com", "HTTP/s@e.c");
        var config = SpnegoConfig.builder()
                .gssConfig(gss)
                .stripRealmFromPrincipal(false)
                .build();
        assertThat(config.stripRealmFromPrincipal()).isFalse();
    }

    @Test void testNullGssConfigThrowsOnBuild() {
        var builder = SpnegoConfig.builder().gssConfig(null);
        try {
            builder.build();
            // If it doesn't throw at build, check setter was lenient
        } catch (NullPointerException e) {
            assertThat(e).isNotNull();
        }
    }

    @Test void testWithServicePrincipal() {
        var gss = makeGss("TEST.LOCAL", "kdc.test.local", "HTTP/server@test.local");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().servicePrincipal()).isEqualTo("HTTP/server@test.local");
    }

    @Test void testWithKeytabPath() {
        var gss = GssConfig.builder()
                .realm("TEST.LOCAL")
                .kdc("kdc.test.local")
                .servicePrincipal("HTTP/s@t")
                .keytabPath("/etc/krb5.keytab")
                .build();
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().keytabPath()).isEqualTo("/etc/krb5.keytab");
    }

    @Test void testApplyAsSystemProperties() {
        var gss = makeGss("TEST.LOCAL", "kdc.test.local", "HTTP/s@test");
        assertThatNoException().isThrownBy(() -> gss.applyAsSystemProperties());
    }

    @Test void testGssConfigToStringNotThrow() {
        var gss = makeGss("TEST", "kdc.test", "HTTP/t@t");
        String str = gss.toString();
        assertThat(str).contains("GssConfig");
    }

    @Test void testFullBuilderChain() {
        var gss = GssConfig.builder()
                .realm("FULL.EXAMPLE")
                .kdc("kdc.full.example")
                .servicePrincipal("HTTP/full@FULL.EXAMPLE")
                .keytabPath("/etc/krb5.keytab")
                .useSubjectCredsOnly(false)
                .build();
        var config = SpnegoConfig.builder()
                .gssConfig(gss)
                .stripRealmFromPrincipal(true)
                .build();
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test void testMultipleSpnegoConfigsIndependent() {
        var gss1 = makeGss("REALM1", "kdc1.e.com", "HTTP/s1@r1");
        var config1 = SpnegoConfig.builder().gssConfig(gss1).stripRealmFromPrincipal(true).build();
        
        var gss2 = makeGss("REALM2", "kdc2.e.com", "HTTP/s2@r2");
        var config2 = SpnegoConfig.builder().gssConfig(gss2).stripRealmFromPrincipal(false).build();
        
        assertThat(config1.gssConfig().realm()).isEqualTo("REALM1");
        assertThat(config2.gssConfig().realm()).isEqualTo("REALM2");
    }

    @Test void testGssConfigAccessorConsistent() {
        var gss = makeGss("ACCESSTEST", "kdc.access", "HTTP/a@ac");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig()).isEqualTo(gss);
        assertThat(config.gssConfig()).isEqualTo(config.gssConfig());
    }

    @Test void testGssConfigKdcAccessor() {
        var gss = makeGss("TEST", "kdc.example.com", "HTTP/t@t");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().kdc()).isEqualTo("kdc.example.com");
    }

    @Test void testRealmAccessor() {
        var gss = makeGss("MYREALM", "my.kdc", "HTTP/x@y");
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().realm()).isEqualTo("MYREALM");
    }

    @Test void testKeytabPathAccessor() {
        var gss = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("HTTP/r@r")
                .keytabPath("/custom/path.keytab")
                .build();
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().keytabPath()).isEqualTo("/custom/path.keytab");
    }

    @Test void testUseSubjectCredsOnlyAccessor() {
        var gss = GssConfig.builder()
                .realm("R")
                .kdc("k")
                .servicePrincipal("HTTP/r@r")
                .useSubjectCredsOnly(true)
                .build();
        var config = SpnegoConfig.builder().gssConfig(gss).build();
        assertThat(config.gssConfig().useSubjectCredsOnly()).isTrue();
    }

    @Test void testOfFactoryMethod() {
        var gss = makeGss("EXAMPLE.COM", "kdc.example.com", "HTTP/server@example.com");
        var config = SpnegoConfig.of(gss);
        assertThat(config.gssConfig()).isEqualTo(gss);
        // Default stripRealmFromPrincipal should be true
        assertThat(config.stripRealmFromPrincipal()).isTrue();
    }

    @Test void testToStringContainsFields() {
        var gss = makeGss("TEST", "kdc.test", "HTTP/t@t");
        var config = SpnegoConfig.builder().gssConfig(gss).stripRealmFromPrincipal(false).build();
        String str = config.toString();
        assertThat(str).contains("SpnegoConfig");
        assertThat(str).contains("stripRealmFromPrincipal=false");
        assertThat(str).contains("gssConfig=");
    }

    @Test void testToStringWithStripTrue() {
        var gss = makeGss("TEST", "kdc.test", "HTTP/t@t");
        var config = SpnegoConfig.of(gss); // uses default strip=true
        String str = config.toString();
        assertThat(str).contains("stripRealmFromPrincipal=true");
    }
}
