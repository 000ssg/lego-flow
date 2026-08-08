package ssg.legoflow.ftp.security;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class FtpsConfigTest {

    @Test
    void testTrustAllReturnsExplicitMode() {
        FtpsConfig config = FtpsConfig.trustAll();
        
        assertThat(config.mode()).isEqualTo(FtpsMode.EXPLICIT);
        assertThat(config.keystorePath()).isNull();
        assertThat(config.clientAuth()).isFalse();
    }

    @Test
    void testBuilderDefaultValues() {
        FtpsConfig config = FtpsConfig.builder().build();
        
        assertThat(config.keystorePath()).isNull();
        assertThat(config.keystorePassword()).isEmpty();
        assertThat(config.keystoreType()).isEqualTo("JKS");
        assertThat(config.truststorePath()).isNull();
        assertThat(config.truststorePassword()).isEmpty();
        assertThat(config.truststoreType()).isEqualTo("JKS");
        assertThat(config.protocols()).containsExactly("TLSv1.2", "TLSv1.3");
        assertThat(config.cipherSuites()).isNull();
        assertThat(config.mode()).isEqualTo(FtpsMode.EXPLICIT);
        assertThat(config.clientAuth()).isFalse();
    }

    @Test
    void testBuilderWithKeystore() {
        Path ksPath = Path.of("/path/to/keystore.jks");
        
        FtpsConfig config = FtpsConfig.builder()
                .keystorePath(ksPath)
                .keystorePassword("changeit")
                .keystoreType("PKCS12")
                .build();
        
        assertThat(config.keystorePath()).isEqualTo(ksPath);
        assertThat(config.keystorePassword()).isEqualTo("changeit".toCharArray());
        assertThat(config.keystoreType()).isEqualTo("PKCS12");
    }

    @Test
    void testBuilderWithTruststore() {
        Path tsPath = Path.of("/path/to/truststore.jks");
        
        FtpsConfig config = FtpsConfig.builder()
                .truststorePath(tsPath)
                .truststorePassword("password123")
                .truststoreType("JCEKS")
                .build();
        
        assertThat(config.truststorePath()).isEqualTo(tsPath);
        assertThat(config.truststorePassword()).isEqualTo("password123".toCharArray());
        assertThat(config.truststoreType()).isEqualTo("JCEKS");
    }

    @Test
    void testBuilderWithCustomProtocols() {
        FtpsConfig config = FtpsConfig.builder()
                .protocols("TLSv1.3")
                .build();
        
        assertThat(config.protocols()).containsExactly("TLSv1.3");
    }

    @Test
    void testBuilderWithMultipleProtocols() {
        FtpsConfig config = FtpsConfig.builder()
                .protocols("TLSv1.2", "TLSv1.3", "SSLv3")
                .build();
        
        assertThat(config.protocols()).containsExactly("TLSv1.2", "TLSv1.3", "SSLv3");
    }

    @Test
    void testBuilderWithCipherSuites() {
        FtpsConfig config = FtpsConfig.builder()
                .cipherSuites(
                        "TLS_AES_256_GCM_SHA384",
                        "TLS_CHACHA20_POLY1305_SHA256"
                )
                .build();
        
        assertThat(config.cipherSuites()).hasSize(2);
    }

    @Test
    void testBuilderWithExplicitMode() {
        FtpsConfig config = FtpsConfig.builder()
                .mode(FtpsMode.EXPLICIT)
                .build();
        
        assertThat(config.mode()).isEqualTo(FtpsMode.EXPLICIT);
    }

    @Test
    void testBuilderWithImplicitMode() {
        FtpsConfig config = FtpsConfig.builder()
                .mode(FtpsMode.IMPLICIT)
                .build();
        
        assertThat(config.mode()).isEqualTo(FtpsMode.IMPLICIT);
    }

    @Test
    void testBuilderWithClientAuthEnabled() {
        FtpsConfig config = FtpsConfig.builder()
                .clientAuth(true)
                .build();
        
        assertThat(config.clientAuth()).isTrue();
    }

    @Test
    void testBuilderWithClientAuthDisabled() {
        FtpsConfig config = FtpsConfig.builder()
                .clientAuth(false)
                .build();
        
        assertThat(config.clientAuth()).isFalse();
    }

    @Test
    void testBuilderFullConfiguration() {
        Path ksPath = Path.of("/path/to/keystore.p12");
        Path tsPath = Path.of("/path/to/truststore.jks");
        
        FtpsConfig config = FtpsConfig.builder()
                .keystorePath(ksPath)
                .keystorePassword("ksPass")
                .keystoreType("PKCS12")
                .truststorePath(tsPath)
                .truststorePassword("tsPass")
                .truststoreType("JKS")
                .protocols("TLSv1.3")
                .cipherSuites("TLS_AES_256_GCM_SHA384")
                .mode(FtpsMode.IMPLICIT)
                .clientAuth(true)
                .build();
        
        assertThat(config.keystorePath()).isEqualTo(ksPath);
        assertThat(config.keystoreType()).isEqualTo("PKCS12");
        assertThat(config.truststorePath()).isEqualTo(tsPath);
        assertThat(config.protocols()).containsExactly("TLSv1.3");
        assertThat(config.cipherSuites()).containsExactly("TLS_AES_256_GCM_SHA384");
        assertThat(config.mode()).isEqualTo(FtpsMode.IMPLICIT);
        assertThat(config.clientAuth()).isTrue();
    }

    @Test
    void testBuilderReturnsSameInstanceForChaining() {
        FtpsConfig.Builder builder = FtpsConfig.builder();
        FtpsConfig.Builder result = builder
                .keystoreType("PKCS12")
                .mode(FtpsMode.IMPLICIT)
                .clientAuth(true);
        assertThat(result).isSameAs(builder);
    }

    @Test
    void testBuilderProtocolsAreCloned() {
        String[] protocols = new String[]{"TLSv1.2", "TLSv1.3"};
        
        FtpsConfig config = FtpsConfig.builder()
                .protocols(protocols)
                .build();
        
        // Mutating original array should not affect the config
        protocols[0] = "SSLv3";
        assertThat(config.protocols()).containsExactly("TLSv1.2", "TLSv1.3");
    }

    @Test
    void testBuilderCipherSuitesAreCloned() {
        String[] suites = new String[]{"TLS_AES_256_GCM_SHA384"};
        
        FtpsConfig config = FtpsConfig.builder()
                .cipherSuites(suites)
                .build();
        
        // Mutating original array should not affect the config
        suites[0] = "NONE";
        assertThat(config.cipherSuites()).containsExactly("TLS_AES_256_GCM_SHA384");
    }

    @Test
    void testModeEnumValues() {
        FtpsMode[] values = FtpsMode.values();
        assertThat(values).hasSize(2);
        assertThat(values).contains(FtpsMode.EXPLICIT, FtpsMode.IMPLICIT);
    }
}
