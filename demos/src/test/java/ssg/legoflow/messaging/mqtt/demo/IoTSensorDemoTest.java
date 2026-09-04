package ssg.legoflow.messaging.mqtt.demo;

import ssg.legoflow.messaging.mqtt.broker.MqttBroker;
import ssg.legoflow.messaging.mqtt.broker.MqttBrokerConfig;
import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
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
 * @since 0.1.0
 */
class IoTSensorDemoTest {

    @Test
    void testSingleSensorPublishesData() throws Exception {
        // Given: dashboard subscriber
        try (var broker = new MqttBroker(MqttBrokerConfig.minimal())) {
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new ConcurrentHashMap<String, List<String>>();
            var latch = new CountDownLatch(1);

            try (var dashboard = client(port, "iot-dash-1")) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>())
                            .add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                try (var sensor = client(port, "sensor-1")) {
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
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new ConcurrentHashMap<String, List<String>>();
            var latch = new CountDownLatch(3);

            try (var dashboard = client(port, "iot-dash-2")) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.computeIfAbsent(t, k -> new CopyOnWriteArrayList<>())
                            .add(new String(p, StandardCharsets.UTF_8));
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                for (int i = 1; i <= 3; i++) {
                    try (var sensor = client(port, "s-" + i)) {
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
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var topics = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(2);

            try (var dashboard = client(port, "iot-dash-3")) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/#", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    topics.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                try (var sensor = client(port, "multi-metric")) {
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
            broker.bind("localhost", 0);
            int port = broker.getPort();
            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var dashboard = client(port, "iot-dash-4")) {
                dashboard.connect().get(5, TimeUnit.SECONDS);
                dashboard.subscribe("iot/sensors/+/temp", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                    received.add(t);
                    latch.countDown();
                }).get(5, TimeUnit.SECONDS);

                try (var sensor = client(port, "selective-s")) {
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
            broker.bind("localhost", 0);
            int port = broker.getPort();

            try (var sensor = client(port, "retain-sensor")) {
                sensor.connect().get(5, TimeUnit.SECONDS);
                sensor.publish("iot/sensors/retain-sensor/temp", "28.5".getBytes(),
                        QoS.AT_LEAST_ONCE, true).get(5, TimeUnit.SECONDS);
            }

            Thread.sleep(200);

            var received = new CopyOnWriteArrayList<String>();
            var latch = new CountDownLatch(1);

            try (var dashboard = client(port, "late-dash")) {
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

    private MqttClient client(int port, String clientId) {
        return new MqttClient(MqttClientConfig.defaults()
                .host("localhost").port(port).clientId(clientId).build());
    }
}
