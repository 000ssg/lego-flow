package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link SmartHomeDemo}.
 *
 * @since 0.1.0
 */
class SmartHomeDemoTest {

    private SmartHomeDemo demo;

    @BeforeEach
    void setUp() {
        demo = new SmartHomeDemo();
        demo.setup("example.com");
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testSmartHomeSetup() {
        assertThat(demo.getHubClient().isConnected()).isTrue();
        assertThat(demo.getUserClient().isConnected()).isTrue();
        assertThat(demo.getHubClient().getSensorManager().getSensors()).hasSize(2);
    }

    @Test
    void testReadAllSensors() {
        var readings = demo.readAllSensors();
        assertThat(readings).hasSize(2);
        assertThat(readings.get(0).fields()).isNotEmpty();
    }

    @Test
    void testTemperatureDropTriggersAutomation() {
        demo.simulateTemperatureChange(18.0);
        assertThat(demo.getNotifications()).isNotEmpty();
        assertThat(demo.getThermostat().getParameter("targetTemp").value()).isEqualTo("23.0");
    }

    @Test
    void testTemperatureRiseTriggersAutomation() {
        demo.simulateTemperatureChange(28.0);
        assertThat(demo.getNotifications()).isNotEmpty();
        assertThat(demo.getThermostat().getParameter("targetTemp").value()).isEqualTo("22.0");
    }

    @Test
    void testMotionDetection() {
        demo.simulateMotion(true);
        assertThat(demo.getNotifications()).isNotEmpty();
        assertThat(demo.getLights().getParameter("on").value()).isEqualTo("true");
        assertThat(demo.getLights().getParameter("brightness").value()).isEqualTo("100");
    }

    @Test
    void testDiscoveryRegistry() {
        var registry = demo.getHubClient().getDiscoveryManager().getRegistry();
        assertThat(registry.size()).isEqualTo(4);
    }
}
