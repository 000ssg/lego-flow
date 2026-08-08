package ssg.legoflow.xmpp.iot.sensor;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SensorData} and {@link SensorField}.
 *
 * @since 0.1.0
 */
class SensorDataTest {

    @Test
    void testSensorDataCreation() {
        var fields = List.of(
                SensorField.numeric("temperature", 22.5, "C"),
                SensorField.numeric("humidity", 45.0, "%"));
        var data = new SensorData("sensor-1", Instant.now(), fields);
        assertThat(data.nodeId()).isEqualTo("sensor-1");
        assertThat(data.fields()).hasSize(2);
    }

    @Test
    void testGetField() {
        var fields = List.of(
                SensorField.numeric("temperature", 22.5, "C"),
                SensorField.bool("enabled", true));
        var data = new SensorData("s1", Instant.now(), fields);
        assertThat(data.getField("temperature")).isNotNull();
        assertThat(data.getField("temperature").value()).isEqualTo("22.5");
        assertThat(data.getField("missing")).isNull();
    }

    @Test
    void testGetFieldsByType() {
        var fields = List.of(
                SensorField.numeric("temp", 22.5, "C"),
                SensorField.numeric("humidity", 45.0, "%"),
                SensorField.string("location", "Room 1"));
        var data = new SensorData("s1", Instant.now(), fields);
        assertThat(data.getFieldsByType(SensorField.SensorFieldType.NUMERIC)).hasSize(2);
        assertThat(data.getFieldsByType(SensorField.SensorFieldType.STRING)).hasSize(1);
    }

    @Test
    void testNumericField() {
        var field = SensorField.numeric("temperature", 22.5, "C");
        assertThat(field.name()).isEqualTo("temperature");
        assertThat(field.type()).isEqualTo(SensorField.SensorFieldType.NUMERIC);
        assertThat(field.unit()).isEqualTo("C");
        assertThat(field.writable()).isFalse();
    }

    @Test
    void testBooleanField() {
        var field = SensorField.bool("enabled", true);
        assertThat(field.type()).isEqualTo(SensorField.SensorFieldType.BOOLEAN);
        assertThat(field.value()).isEqualTo("true");
    }

    @Test
    void testToXml() {
        var fields = List.of(SensorField.numeric("temp", 22.5, "C"));
        var data = new SensorData("s1", Instant.parse("2024-01-01T00:00:00Z"), fields);
        var xml = data.toXml();
        assertThat(xml).contains("xmlns=\"urn:xmpp:iot:sensordata\"");
        assertThat(xml).contains("nodeId=\"s1\"");
        assertThat(xml).contains("<numeric");
    }
}
