package ssg.legoflow.http.auth.sso.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive SSO demo and verifies all feature sections.
 *
 * @since 0.1.0
 */
class DemoSsoAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSsoAll.runAll();

        assertThat(results.ssoManager())
                .as("SSO Manager (JWT-based login, validate, logout)")
                .isTrue();

        assertThat(results.ssoSession())
                .as("SSO Session (federated services, attributes, expiration)")
                .isTrue();

        assertThat(results.reverseProxySso())
                .as("Reverse Proxy SSO (header injection, principal extraction)")
                .isTrue();

        assertThat(results.samlAuthnRequest())
                .as("SAML AuthnRequest (XML, redirect binding, POST binding)")
                .isTrue();

        assertThat(results.samlAssertionParsing())
                .as("SAML assertion parsing (NameID, Issuer, Attributes, Conditions)")
                .isTrue();

        assertThat(results.samlLogout())
                .as("SAML logout (request generation, response parsing)")
                .isTrue();
    }
}
