package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link IoTControlDemo}.
 *
 * @since 1.0.0
 */
class IoTControlDemoTest {

    private IoTControlDemo demo;

    @BeforeEach
    void setUp() {
        demo = new IoTControlDemo();
        demo.setup("example.com");
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testThermostatSetup() {
        assertThat(demo.getThermostat().getParameters()).hasSize(3);
    }

    @Test
    void testSetTemperature() {
        boolean success = demo.setTemperature(25.0);
        assertThat(success).isTrue();
        assertThat(demo.getThermostat().getParameter("targetTemp").value()).isEqualTo("25.0");
    }

    @Test
    void testSetMode() {
        boolean success = demo.setMode("heat");
        assertThat(success).isTrue();
        assertThat(demo.getThermostat().getParameter("mode").value()).isEqualTo("heat");
    }

    @Test
    void testSetEnabled() {
        boolean success = demo.setEnabled(false);
        assertThat(success).isTrue();
        assertThat(demo.getThermostat().getParameter("enabled").value()).isEqualTo("false");
    }

    @Test
    void testControlHistory() {
        demo.setTemperature(23.0);
        demo.setMode("cool");
        assertThat(demo.getControlHistory()).hasSize(2);
    }
}
