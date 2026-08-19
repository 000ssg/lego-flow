package ssg.legoflow.coap.demo;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Runs the comprehensive CoAP demo and verifies all feature sections.
 *
 * <p>By default, uses the in-house {@code CoapServer}. To test against
 * an external CoAP server (Californium, libcoap), set
 * {@code DemoCoapAll.USE_EXTERNAL = true} and configure host/port.</p>
 *
 * @since 0.1.0
 */
class DemoCoapAllTest {

    @Test
    void testAllFeatures() throws Exception {
        var results = DemoCoapAll.runAll();

        assertThat(results.getSensorOk())
                .as("GET sensor returns 2.05 Content with correct value")
                .isTrue();

        assertThat(results.putSensorOk())
                .as("PUT updates sensor value and re-GET confirms")
                .isTrue();

        assertThat(results.postItemOk())
                .as("POST creates a new item (2.01 Created)")
                .isTrue();

        assertThat(results.deleteItemOk())
                .as("DELETE removes an item (2.02 Deleted)")
                .isTrue();

        assertThat(results.observeCount())
                .as("Observe delivers at least 2 notifications")
                .isGreaterThanOrEqualTo(2);

        assertThat(results.discoveryLinks())
                .as("Resource discovery finds at least 4 links")
                .isGreaterThanOrEqualTo(4);

        assertThat(results.contentFormatOk())
                .as("Content format negotiation returns JSON")
                .isTrue();

        assertThat(results.gatewayNodeCount())
                .as("IoT gateway manages 3 sensor nodes")
                .isEqualTo(3);

        assertThat(results.blockTransferOk())
                .as("Large payload retrieved via blockwise transfer")
                .isTrue();
    }
}
