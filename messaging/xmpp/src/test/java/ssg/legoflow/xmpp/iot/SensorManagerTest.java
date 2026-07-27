package ssg.legoflow.xmpp.iot;

import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.sensor.SensorData;
import ssg.legoflow.xmpp.iot.sensor.SensorField;
import ssg.legoflow.xmpp.iot.sensor.SensorNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link SensorManager}.
 *
 * @since 1.0.0
 */
class SensorManagerTest {

    private SensorManager manager;

    @BeforeEach
    void setUp() {
        manager = new SensorManager();
    }

    @Test
    void testRegisterSensor() {
        var node = new SensorNode("temp-1", "building");
        node.addField(SensorField.numeric("temperature", 22.5, "C"));
        manager.registerSensor(node);
        assertThat(manager.getSensors()).hasSize(1);
        assertThat(manager.getSensor("temp-1")).isNotNull();
    }

    @Test
    void testRequestSensorDataLocal() {
        var node = new SensorNode("temp-1", "building");
        node.addField(SensorField.numeric("temperature", 22.5, "C"));
        manager.registerSensor(node);
        var data = manager.requestSensorData(JID.parse("temp-1@localhost")).join();
        assertThat(data.nodeId()).isEqualTo("temp-1");
        assertThat(data.fields()).hasSize(1);
    }

    @Test
    void testRequestSensorDataRemote() {
        var data = manager.requestSensorData(JID.parse("remote@other.com")).join();
        assertThat(data).isNotNull();
        assertThat(data.fields()).isEmpty();
    }

    @Test
    void testSubscribeSensorData() {
        var received = new ArrayList<SensorData>();
        manager.subscribeSensorData(JID.parse("sensor@example.com"),
                Duration.ofSeconds(10), received::add);
        var data = new SensorData("sensor", Instant.now(),
                List.of(SensorField.numeric("temp", 22.5, "C")));
        manager.notifySensorData(JID.parse("sensor@example.com"), data);
        assertThat(received).hasSize(1);
    }

    @Test
    void testGlobalListener() {
        var received = new ArrayList<SensorData>();
        manager.addGlobalListener(received::add);
        var data = new SensorData("any", Instant.now(), List.of());
        manager.notifySensorData(JID.parse("any@example.com"), data);
        assertThat(received).hasSize(1);
    }

    @Test
    void testGetSensors() {
        manager.registerSensor(new SensorNode("s1", "src"));
        manager.registerSensor(new SensorNode("s2", "src"));
        assertThat(manager.getSensors()).hasSize(2);
    }
}
