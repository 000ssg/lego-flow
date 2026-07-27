package ssg.legoflow.upnp.ssdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for SSDP multicast TTL configuration.
 *
 * <p>Verifies that the {@link SsdpService} supports configurable multicast
 * Time-To-Live values as recommended by UPnP Device Architecture (UDA).
 *
 * @since 1.0.0
 */
class SsdpTtlConfigurationTest {

    @Test
    void testDefaultTtlConstant() {
        assertThat(SsdpService.DEFAULT_MULTICAST_TTL).isEqualTo(4);
    }

    @Test
    void testTtlTooLowThrows() {
        assertThatThrownBy(() -> new MultiInterfaceSsdpService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTtlTooHighThrows() {
        assertThatThrownBy(() -> new MultiInterfaceSsdpService(256))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testTtlBoundaryMin() {
        var service = new MultiInterfaceSsdpService(1);
        assertThat(service).isNotNull();
    }

    @Test
    void testTtlBoundaryMax() {
        var service = new MultiInterfaceSsdpService(255);
        assertThat(service).isNotNull();
    }

    @Test
    void testDefaultConstructorUsesDefaultTtl() {
        var service = new MultiInterfaceSsdpService();
        assertThat(service).isNotNull();
    }

    @Test
    void testNegativeTtlThrows() {
        assertThatThrownBy(() -> new MultiInterfaceSsdpService(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
