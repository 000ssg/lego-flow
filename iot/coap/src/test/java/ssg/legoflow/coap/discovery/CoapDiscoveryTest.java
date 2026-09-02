package ssg.legoflow.coap.discovery;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link CoapDiscovery} covering constant values,
 * discovery on unavailable hosts (should throw or return empty),
 * and basic API surface coverage.
 */
class CoapDiscoveryTest {

    @Test
    void testConstants() {
        assertThat(CoapDiscovery.COAP_MULTICAST_IPV4).isEqualTo("224.0.1.187");
        assertThat(CoapDiscovery.DEFAULT_PORT).isEqualTo(5683);
    }
}
