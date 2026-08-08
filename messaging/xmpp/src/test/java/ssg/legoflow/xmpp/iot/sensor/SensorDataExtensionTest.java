package ssg.legoflow.xmpp.iot.sensor;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SensorDataExtension}.
 *
 * @since 0.1.0
 */
class SensorDataExtensionTest {

    @Test
    void testNamespace() {
        var ext = SensorDataExtension.req("1");
        assertThat(ext.getNamespace()).isEqualTo("urn:xmpp:iot:sensordata");
    }

    @Test
    void testReqElement() {
        var ext = SensorDataExtension.req("42");
        assertThat(ext.getElementName()).isEqualTo("req");
        assertThat(ext.toXml()).contains("seqnr=\"42\"");
    }

    @Test
    void testFieldsElement() {
        var data = new SensorData("s1", Instant.now(),
                List.of(SensorField.numeric("temp", 22.5, "C")));
        var ext = SensorDataExtension.fields(data, "1");
        assertThat(ext.getElementName()).isEqualTo("fields");
        assertThat(ext.getSensorData()).isNotNull();
        assertThat(ext.toXml()).contains("urn:xmpp:iot:sensordata");
    }

    @Test
    void testDoneElement() {
        var ext = SensorDataExtension.done("1");
        assertThat(ext.getElementName()).isEqualTo("done");
        assertThat(ext.toXml()).contains("done");
    }

    @Test
    void testParseFields() {
        String xml = "<numeric name=\"temp\" value=\"22.5\" unit=\"C\"/>" +
                "<boolean name=\"on\" value=\"true\"/>";
        var fields = SensorDataExtension.parseFields(xml);
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).name()).isEqualTo("temp");
        assertThat(fields.get(0).type()).isEqualTo(SensorField.SensorFieldType.NUMERIC);
        assertThat(fields.get(1).type()).isEqualTo(SensorField.SensorFieldType.BOOLEAN);
    }
}
