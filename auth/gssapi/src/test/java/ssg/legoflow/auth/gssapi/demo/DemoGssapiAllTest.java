package ssg.legoflow.auth.gssapi.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive GSSAPI demo and verifies all feature sections.
 *
 * <p>By default, runs without a Kerberos KDC. To test with a live KDC, set
 * {@code DemoGssapiAll.USE_EXTERNAL = true} and configure realm/KDC/principal.</p>
 *
 * @since 1.0.0
 */
class DemoGssapiAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoGssapiAll.runAll();

        assertThat(results.oidConstants())
                .as("OID constants (KERBEROS_V5, SPNEGO, KRB5_PRINCIPAL_NAME)")
                .isTrue();

        assertThat(results.configBuilder())
                .as("GssConfig builder with realm, KDC, principal, keytab")
                .isTrue();

        assertThat(results.spnegoNegTokenInit())
                .as("SPNEGO NegTokenInit creation and mechToken extraction")
                .isTrue();

        assertThat(results.spnegoNegTokenResp())
                .as("SPNEGO NegTokenResp with accept-completed and accept-incomplete")
                .isTrue();

        assertThat(results.spnegoDetection())
                .as("SPNEGO token detection (NegTokenInit, NegTokenResp, raw, null)")
                .isTrue();

        assertThat(results.base64RoundTrip())
                .as("Base64 encode/decode round-trip for SPNEGO tokens")
                .isTrue();
    }
}
