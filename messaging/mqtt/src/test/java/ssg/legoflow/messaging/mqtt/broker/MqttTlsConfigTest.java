package ssg.legoflow.messaging.mqtt.broker;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link MqttTlsConfig}.
 *
 * @since 0.1.0
 */
class MqttTlsConfigTest {

    @Test
    void testBuilderWithDefaults() {
        // Given/When: build with required fields
        var config = MqttTlsConfig.builder()
                .keystorePath("/path/to/keystore.p12")
                .keystorePassword("changeit")
                .build();

        // Then: defaults applied
        assertThat(config.keystorePath()).isEqualTo("/path/to/keystore.p12");
        assertThat(config.keystorePassword()).isEqualTo("changeit");
        assertThat(config.truststorePath()).isNull();
        assertThat(config.truststorePassword()).isNull();
        assertThat(config.protocols()).containsExactly("TLSv1.3", "TLSv1.2");
        assertThat(config.cipherSuites()).isEmpty();
    }

    @Test
    void testBuilderWithAllFields() {
        // Given/When: build with all fields
        var config = MqttTlsConfig.builder()
                .keystorePath("/path/to/keystore.p12")
                .keystorePassword("kspass")
                .truststorePath("/path/to/truststore.p12")
                .truststorePassword("tspass")
                .protocols(List.of("TLSv1.3"))
                .cipherSuites(List.of("TLS_AES_256_GCM_SHA384"))
                .build();

        // Then: all values set
        assertThat(config.keystorePath()).isEqualTo("/path/to/keystore.p12");
        assertThat(config.keystorePassword()).isEqualTo("kspass");
        assertThat(config.truststorePath()).isEqualTo("/path/to/truststore.p12");
        assertThat(config.truststorePassword()).isEqualTo("tspass");
        assertThat(config.protocols()).containsExactly("TLSv1.3");
        assertThat(config.cipherSuites()).containsExactly("TLS_AES_256_GCM_SHA384");
    }

    @Test
    void testBuilderRequiresKeystorePath() {
        // Given: builder without keystore path
        var builder = MqttTlsConfig.builder()
                .keystorePassword("changeit");

        // Then: build fails
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keystore path and password");
    }

    @Test
    void testBuilderRequiresKeystorePassword() {
        // Given: builder without keystore password
        var builder = MqttTlsConfig.builder()
                .keystorePath("/path/to/keystore.p12");

        // Then: build fails
        assertThatThrownBy(builder::build)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keystore path and password");
    }

    @Test
    void testProtocolsListIsImmutable() {
        // Given: config with default protocols
        var config = MqttTlsConfig.builder()
                .keystorePath("/ks.p12")
                .keystorePassword("pass")
                .build();

        // Then: protocols list is immutable
        assertThatThrownBy(() -> config.protocols().add("SSLv3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testCipherSuitesListIsImmutable() {
        // Given: config with cipher suites
        var config = MqttTlsConfig.builder()
                .keystorePath("/ks.p12")
                .keystorePassword("pass")
                .cipherSuites(List.of("TLS_AES_128_GCM_SHA256"))
                .build();

        // Then: cipher suites list is immutable
        assertThatThrownBy(() -> config.cipherSuites().add("weak-cipher"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testCreateSslContextFailsWithInvalidKeystore() {
        // Given: config with non-existent keystore
        var config = MqttTlsConfig.builder()
                .keystorePath("/nonexistent/keystore.p12")
                .keystorePassword("changeit")
                .build();

        // Then: createSslContext throws
        assertThatThrownBy(config::createSslContext)
                .isInstanceOf(Exception.class);
    }
}
