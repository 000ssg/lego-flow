package ssg.legoflow.http.auth.spnego.demo;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the comprehensive SPNEGO demo and verifies all feature sections.
 *
 * <p>By default, runs without a Kerberos KDC. To test with a live KDC, set
 * {@code DemoSpnegoAll.USE_EXTERNAL = true} and configure realm/KDC/principal.</p>
 *
 * @since 1.0.0
 */
class DemoSpnegoAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoSpnegoAll.runAll();

        assertThat(results.spnegoConfig())
                .as("SpnegoConfig builder (GssConfig + realm stripping)")
                .isTrue();

        assertThat(results.negotiateScheme())
                .as("NegotiateAuthScheme (name, challenge on missing header)")
                .isTrue();

        assertThat(results.tokenExtraction())
                .as("Token extraction (Negotiate header, None for missing/wrong)")
                .isTrue();

        assertThat(results.challengeGeneration())
                .as("WWW-Authenticate: Negotiate challenge header")
                .isTrue();

        assertThat(results.spnegoIntegration())
                .as("SPNEGO token processing flow (create, encode, detect)")
                .isTrue();
    }
}
