package ssg.legoflow.xmpp.demo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link IoTSensorDemo}.
 *
 * @since 0.1.0
 */
class IoTSensorDemoTest {

    private IoTSensorDemo demo;

    @BeforeEach
    void setUp() {
        demo = new IoTSensorDemo();
        demo.setup("example.com");
    }

    @AfterEach
    void tearDown() {
        demo.shutdown();
    }

    @Test
    void testSensorsRegistered() {
        assertThat(demo.getClient().getSensorManager().getSensors()).hasSize(2);
    }

    @Test
    void testReadTemperatureSensor() {
        var data = demo.readSensor("temp-sensor-1");
        assertThat(data.fields()).hasSize(3);
        assertThat(data.getField("temperature")).isNotNull();
        assertThat(data.getField("battery")).isNotNull();
        assertThat(data.getField("location")).isNotNull();
    }

    @Test
    void testReadHumiditySensor() {
        var data = demo.readSensor("humidity-sensor-1");
        assertThat(data.fields()).hasSize(2);
        assertThat(data.getField("humidity")).isNotNull();
    }

    @Test
    void testUpdateSensorValue() {
        demo.updateSensor("temp-sensor-1", "temperature", "25.0");
        var data = demo.readSensor("temp-sensor-1");
        assertThat(data.getField("temperature").value()).isEqualTo("25.0");
    }

    @Test
    void testReadingsAccumulate() {
        demo.readSensor("temp-sensor-1");
        demo.readSensor("humidity-sensor-1");
        assertThat(demo.getReadings()).hasSize(2);
    }
}
