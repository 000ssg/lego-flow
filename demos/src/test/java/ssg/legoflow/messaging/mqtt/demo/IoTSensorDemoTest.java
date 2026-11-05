package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link IoTSensorDemo} scenarios.
 *
 * <p>All tests run against an in-house {@link MqttBroker} over in-memory transport
 * pairs — no network.</p>
 *
 * @since 0.1.0
 */
class IoTSensorDemoTest {

    @Test
    void testSingleSensorPublishesData() throws Exception {
        // Given: dashboard subscriber
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var received = new ConcurrentHashMap<String, List<String>>();
            var latch = new CountDownLatch(1);

            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults().clientId("iot-dash-1").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>())
                            .add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                var sensorPair = InMemoryMqttTransport.createPair();
                broker.handleConnection(sensorPair[0]);
                try (var sensor = new MqttClient(MqttClientConfig.defaults().clientId("sensor-1").build(), sensorPair[1])) {
                    sensor.connect().get(5, TimeUnit.SECONDS);
                    sensor.publish("iot/sensors/sensor-1/temp", "25.0".getBytes(),
                            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                }

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).containsKey("iot/sensors/sensor-1/temp");
            }
        }
    }

    @Test
    void testMultipleSensorsPublish() throws Exception {
        // Given: dashboard and 3 sensors
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var received = new ConcurrentHashMap<String, List<String>>();
            var latch = new CountDownLatch(3);

            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults().clientId("iot-dash-2").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>())
                            .add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                for (int i = 1; i <= 3; i++) {
                    var sensorPair = InMemoryMqttTransport.createPair();
                    broker.handleConnection(sensorPair[0]);
                    try (var sensor = new MqttClient(MqttClientConfig.defaults().clientId("s-" + i).build(), sensorPair[1])) {
                        sensor.connect().get(5, TimeUnit.SECONDS);
                        sensor.publish("iot/sensors/s-" + i + "/temp", String.valueOf(20 + i).getBytes(),
                                QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                    }
                }

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).hasSize(3);
            }
        }
    }

    @Test
    void testDashboardReceivesDifferentMetrics() throws Exception {
        // Given: sensor publishing temp and humidity
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var topics = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults().clientId("iot-dash-3").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    topics.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                var sensorPair = InMemoryMqttTransport.createPair();
                broker.handleConnection(sensorPair[0]);
                try (var sensor = new MqttClient(MqttClientConfig.defaults().clientId("multi-metric").build(), sensorPair[1])) {
                    sensor.connect().get(5, TimeUnit.SECONDS);
                    sensor.publish("iot/sensors/multi-metric/temp", "22".getBytes(),
                            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                    sensor.publish("iot/sensors/multi-metric/humidity", "50".getBytes(),
                            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                }

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(topics).containsExactlyInAnyOrder(
                        "iot/sensors/multi-metric/temp",
                        "iot/sensors/multi-metric/humidity");
            }
        }
    }

    @Test
    void testSelectiveSubscription() throws Exception {
        // Given: subscriber only to temperature
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults().clientId("iot-dash-4").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/+/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                var sensorPair = InMemoryMqttTransport.createPair();
                broker.handleConnection(sensorPair[0]);
                try (var sensor = new MqttClient(MqttClientConfig.defaults().clientId("selective-s").build(), sensorPair[1])) {
                    sensor.connect().get(5, TimeUnit.SECONDS);
                    sensor.publish("iot/sensors/selective-s/temp", "25".getBytes(),
                            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                    sensor.publish("iot/sensors/selective-s/humidity", "60".getBytes(),
                            QoS.AT_LEAST_ONCE, false).get(5, TimeUnit.SECONDS);
                }

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(300);
                // Only temp received, not humidity
                assertThat(received).containsExactly("iot/sensors/selective-s/temp");
            }
        }
    }

    @Test
    void testSensorRetainedData() throws Exception {
        // Given: sensor publishes retained
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.start();

            var sensorPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(sensorPair[0]);
            try (var sensor = new MqttClient(MqttClientConfig.defaults().clientId("retain-sensor").build(), sensorPair[1])) {
                sensor.connect().get(5, TimeUnit.SECONDS);
                sensor.publish("iot/sensors/retain-sensor/temp", "28.5".getBytes(),
                        QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            var dashPair = InMemoryMqttTransport.createPair();
            broker.handleConnection(dashPair[0]);
            try (var dashboard = new MqttClient(MqttClientConfig.defaults().clientId("late-dash").build(), dashPair[1])) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(received).contains("28.5");
            }
        }
    }
}
