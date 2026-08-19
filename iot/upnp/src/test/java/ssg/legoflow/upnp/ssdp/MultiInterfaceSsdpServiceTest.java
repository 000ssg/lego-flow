package ssg.legoflow.upnp.ssdp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link MultiInterfaceSsdpService} — multi-NIC SSDP discovery.
 *
 * @since 0.1.0
 */
class MultiInterfaceSsdpServiceTest {

    private MultiInterfaceSsdpService service;

    @BeforeEach
    void setUp() {
        service = new MultiInterfaceSsdpService();
    }

    @AfterEach
    void tearDown() throws IOException {
        service.close();
    }

    @Test
    void testInitiallyNotRunning() {
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void testStartSetsRunning() {
        service.start();
        assertThat(service.isRunning()).isTrue();
    }

    @Test
    void testStopClearsRunning() {
        service.start();
        service.stop();
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void testNoServicesInitially() {
        assertThat(service.getServices()).isEmpty();
    }

    @Test
    void testAddNullInterfaceThrows() {
        assertThatThrownBy(() -> service.addInterface(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAddNullListenerThrows() {
        assertThatThrownBy(() -> service.addListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testAddAndRemoveListener() {
        SsdpListener listener = event -> {};
        service.addListener(listener);
        service.removeListener(listener);
        // No exception thrown — listener management works
    }

    @Test
    void testCloseEmptyService() throws IOException {
        service.close();
        assertThat(service.isRunning()).isFalse();
        assertThat(service.getServices()).isEmpty();
    }

    @Test
    void testServicesListIsUnmodifiable() {
        var services = service.getServices();
        assertThatThrownBy(() -> services.add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testCustomTtlConstructor() {
        var customService = new MultiInterfaceSsdpService(8);
        assertThat(customService).isNotNull();
        assertThat(customService.getServices()).isEmpty();
    }
}
