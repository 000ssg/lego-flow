package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * IoT scenario demo: multiple sensors publish telemetry, a dashboard subscribes to all.
 *
 * <p>Runs against an in-house {@link MqttBroker} over in-memory transport pairs —
 * no network, no external broker.</p>
 *
 * @since 0.1.0
 */
public final class IoTSensorDemo {

    private static final Logger LOG = LoggerFactory.getLogger(IoTSensorDemo.class);

    private final Map<String, List<String>> sensorData = new ConcurrentHashMap<>();

    /**
     * Runs the IoT sensor demo with the given number of sensors, against an in-house
     * broker (in-memory transport).
     *
     * @param sensorCount the number of sensors to simulate
     * @throws Exception on error
     */
    public void run(int sensorCount) throws Exception {
        var broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
        try {
            // Dashboard subscriber
            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults()
                    .clientId("dashboard").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (topic, payload, qos, retain) -> {
                    String data = new String(payload, StandardCharsets.UTF_8);
                    sensorData.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(data);
                    LOG.info("Dashboard received: {} = {}", topic, data);
                }).get(5, TimeUnit.SECONDS);

                Thread.sleep(200);

                // Simulate sensors
                for (int i = 1; i <= sensorCount; i++) {
                    String sensorId = "sensor-" + i;
                    var sensorPair = InMemoryMqttTransport.createPair();
                    broker.handleConnection(sensorPair[0]);
                    try (var sensor = new MqttClient(MqttClientConfig.defaults()
                            .clientId(sensorId).build(), sensorPair[1])) {
                        sensor.connect().get(5, TimeUnit.SECONDS);
                        sensor.publish("iot/sensors/" + sensorId + "/temperature",
                                String.valueOf(20.0 + i).getBytes(StandardCharsets.UTF_8),
                                QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                        sensor.publish("iot/sensors/" + sensorId + "/humidity",
                                String.valueOf(40 + i * 2).getBytes(StandardCharsets.UTF_8),
                                QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                    }
                }

                Thread.sleep(500);
            }
        } finally {
            broker.stop();
        }
    }

    /** Returns collected sensor data keyed by topic. */
    public Map<String, List<String>> getSensorData() { return sensorData; }
}
