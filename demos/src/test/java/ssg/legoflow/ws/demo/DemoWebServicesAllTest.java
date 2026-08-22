package ssg.legoflow.ws.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive Web Services demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house web-services components. To test against
 * an external application server, set
 * {@code DemoWebServicesAll.USE_EXTERNAL = true} and configure host/port before running.</p>
 *
 * @since 0.1.0
 */
class DemoWebServicesAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoWebServicesAll.runAll();

        assertThat(results.contentNegotiation())
                .as("Content type negotiation (Accept header, q-factor)")
                .isTrue();

        assertThat(results.routeDispatch())
                .as("Registry-based request routing/dispatch")
                .isTrue();

        assertThat(results.restEndpoints())
                .as("REST endpoint CRUD handling")
                .isTrue();

        assertThat(results.asyncDispatch())
                .as("Async wrapper with CompletableFuture on virtual threads")
                .isTrue();

        assertThat(results.errorHandling())
                .as("Error/exception status codes (404, 405, 406, 400)")
                .isTrue();

        assertThat(results.filterChain())
                .as("Request/response filter chain")
                .isTrue();

        assertThat(results.responseFormats())
                .as("JSON/XML/plain-text response formatting")
                .isTrue();
    }
}
