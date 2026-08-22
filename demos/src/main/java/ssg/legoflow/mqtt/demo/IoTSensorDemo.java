package ssg.legoflow.mqtt.demo;

import ssg.legoflow.mqtt.broker.MqttBroker;
import ssg.legoflow.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.mqtt.client.MqttClient;
import ssg.legoflow.mqtt.client.MqttClientConfig;
import ssg.legoflow.mqtt.protocol.QoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
/**
 * IoT scenario demo: multiple sensors publish telemetry, a dashboard subscribes to all.
 *
 * @since 0.1.0
 */
public final class IoTSensorDemo {

    private static final Logger LOG = LoggerFactory.getLogger(IoTSensorDemo.class);

    private final Map<String, List<String>> sensorData = new ConcurrentHashMap<>();
    private MqttBroker broker;

    /**
     * Runs the IoT sensor demo with the given number of sensors.
     *
     * @param port        the broker port (0 for ephemeral)
     * @param sensorCount the number of sensors to simulate
     * @throws Exception on error
     */
    public void run(int port, int sensorCount) throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", port);
        int actualPort = broker.getPort();

        // Dashboard subscriber
        try (var dashboard = new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(actualPort).clientId("dashboard").build())) {
            dashboard.connect().get();
            dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {
                String data = new String(payload, StandardCharsets.UTF_8);
                sensorData.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(data);
                LOG.info("Dashboard received: {} = {}", topic, data);
            }).get();

            // Simulate sensors
            for (int i = 1; i <= sensorCount; i++) {
                String sensorId = "sensor-" + i;
                try (var sensor = new MqttClient(MqttClientConfig.defaults()
                        .host("localhost").port(actualPort).clientId(sensorId).build())) {
                    sensor.connect().get();
                    sensor.publish("iot/sensors/" + sensorId + "/temperature",
                            String.valueOf(20.0 + i).getBytes(StandardCharsets.UTF_8),
                            QoS.AT_LEAST_ONCE, false).get();
                    sensor.publish("iot/sensors/" + sensorId + "/humidity",
                            String.valueOf(40 + i * 2).getBytes(StandardCharsets.UTF_8),
                            QoS.AT_LEAST_ONCE, false).get();
                }
            }

            Thread.sleep(500);
        }
    }

    /** Returns collected sensor data keyed by topic. */
    public Map<String, List<String>> getSensorData() { return sensorData; }

    /** Stops the broker. */
    public void stop() { if (broker != null) broker.stop(); }
}
