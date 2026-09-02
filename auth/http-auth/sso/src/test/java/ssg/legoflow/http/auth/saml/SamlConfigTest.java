package ssg.legoflow.http.auth.saml;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class SamlConfigTest {

    @Test
    void testFullConstructor() {
        var config = new SamlConfig("https://idp.example.com", "https://idp.example.com/sso",
                "-----BEGIN CERTIFICATE-----\nMIIC...\n-----END CERTIFICATE-----",
                "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
        assertThat(config.entityId()).isEqualTo("https://idp.example.com");
        assertThat(config.ssoUrl()).isEqualTo("https://idp.example.com/sso");
        assertThat(config.certificate()).isNotNull();
        assertThat(config.nameIdFormat()).isEqualTo("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified");
    }

    @Test
    void testDefaultNameIdFormat() {
        var config = new SamlConfig("entity", "https://sso.example.com", null, null);
        assertThat(config.nameIdFormat()).isEqualTo("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
    }

    @Test
    void testNullCertificate() {
        var config = new SamlConfig("entity", "https://sso.example.com", null, null);
        assertThat(config.certificate()).isNull();
    }

    @Test
    void testOfFactory() {
        var config = SamlConfig.of("entity-id", "https://sso.example.com/login");
        assertThat(config.entityId()).isEqualTo("entity-id");
        assertThat(config.ssoUrl()).isEqualTo("https://sso.example.com/login");
        assertThat(config.certificate()).isNull();
        assertThat(config.nameIdFormat()).isEqualTo("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress");
    }

    @Test
    void testNullEntityIdThrows() {
        assertThatThrownBy(() -> new SamlConfig(null, "https://sso.example.com", null, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNullSsoUrlThrows() {
        assertThatThrownBy(() -> new SamlConfig("entity", null, null, null))
                .isInstanceOf(NullPointerException.class);
    }
}
