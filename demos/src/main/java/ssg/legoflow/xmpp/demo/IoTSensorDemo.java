package ssg.legoflow.xmpp.demo;

import ssg.legoflow.xmpp.client.XmppClient;
import ssg.legoflow.xmpp.client.XmppClientConfig;
import ssg.legoflow.xmpp.core.JID;
import ssg.legoflow.xmpp.iot.sensor.SensorData;
import ssg.legoflow.xmpp.iot.sensor.SensorField;
import ssg.legoflow.xmpp.iot.sensor.SensorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
/**
 * IoT Sensor demo: sensor node publishes temperature/humidity, client reads data.
 *
 * @since 0.1.0
 */
public class IoTSensorDemo {

    private static final Logger LOG = LoggerFactory.getLogger(IoTSensorDemo.class);

    private final XmppClient client;
    private final SensorNode temperatureSensor;
    private final SensorNode humiditySensor;
    private final List<SensorData> readings = new ArrayList<>();

    /**
     * Creates the IoT sensor demo.
     */
    public IoTSensorDemo() {
        this.client = new XmppClient();
        this.temperatureSensor = new SensorNode("temp-sensor-1", "building-a");
        this.humiditySensor = new SensorNode("humidity-sensor-1", "building-a");
    }

    /**
     * Sets up the client and sensors.
     *
     * @param domain the XMPP domain
     */
    public void setup(String domain) {
        var config = XmppClientConfig.defaults("localhost", domain);
        client.connect(config).join();
        client.login("sensor-user", "password").join();

        // Configure temperature sensor
        temperatureSensor.addField(SensorField.numeric("temperature", 22.5, "C"));
        temperatureSensor.addField(SensorField.numeric("battery", 95.0, "%"));
        temperatureSensor.addField(SensorField.string("location", "Room 101"));

        // Configure humidity sensor
        humiditySensor.addField(SensorField.numeric("humidity", 45.0, "%"));
        humiditySensor.addField(SensorField.numeric("temperature", 22.3, "C"));

        // Register sensors
        client.getSensorManager().registerSensor(temperatureSensor);
        client.getSensorManager().registerSensor(humiditySensor);

        LOG.info("Set up 2 sensors");
    }

    /**
     * Reads data from a sensor.
     *
     * @param nodeId the sensor node id
     * @return the sensor data
     */
    public SensorData readSensor(String nodeId) {
        var jid = JID.parse(nodeId + "@localhost");
        var data = client.getSensorManager().requestSensorData(jid).join();
        readings.add(data);
        LOG.info("Read {} fields from sensor {}", data.fields().size(), nodeId);
        return data;
    }

    /**
     * Updates a sensor value.
     *
     * @param nodeId    the sensor node id
     * @param fieldName the field name
     * @param value     the new value
     */
    public void updateSensor(String nodeId, String fieldName, String value) {
        if ("temp-sensor-1".equals(nodeId)) {
            temperatureSensor.updateField(fieldName, value);
        } else if ("humidity-sensor-1".equals(nodeId)) {
            humiditySensor.updateField(fieldName, value);
        }
    }

    /** @return all readings */
    public List<SensorData> getReadings() { return List.copyOf(readings); }

    /** @return the client */
    public XmppClient getClient() { return client; }

    /** @return the temperature sensor */
    public SensorNode getTemperatureSensor() { return temperatureSensor; }

    /** @return the humidity sensor */
    public SensorNode getHumiditySensor() { return humiditySensor; }

    /** Shuts down. */
    public void shutdown() { client.close(); }
}
