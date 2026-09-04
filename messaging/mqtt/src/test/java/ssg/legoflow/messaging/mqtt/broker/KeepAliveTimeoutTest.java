package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.client.MqttClient;
import ssg.legoflow.messaging.mqtt.client.MqttClientConfig;
import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for MQTT keep-alive timeout behavior.
 *
 * @since 0.2.0
 */
class KeepAliveTimeoutTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testClientDisconnectedOnKeepAliveTimeout() throws Exception {
        // Given: connect with 1-second keep-alive, then go silent
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);

        var codec = new MqttCodec(MqttVersion.V3_1_1);
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-timeout", true, 1,
                null, null, null, new MqttProperties());
        var connectBuf = codec.encode(connect);
        transports[1].send(connectBuf);

        // Read CONNACK
        var responseBuf = ByteBuffer.allocate(4096);
        int bytesRead = transports[1].receive(responseBuf);
        assertThat(bytesRead).isGreaterThan(0);
        responseBuf.flip();
        var connAck = (ConnAckPacket) codec.decode(responseBuf);
        assertThat(connAck.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);

        // Then: after keep-alive timeout (1.5x interval), broker disconnects
        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("keepalive-timeout"),
                Duration.ofSeconds(5), 50);
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testClientStaysConnectedWithPings() throws Exception {
        // Given: connect with 1-second keep-alive
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);

        var codec = new MqttCodec(MqttVersion.V3_1_1);
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-ping", true, 1,
                null, null, null, new MqttProperties());
        transports[1].send(codec.encode(connect));

        // Read CONNACK
        var responseBuf = ByteBuffer.allocate(4096);
        int bytesRead = transports[1].receive(responseBuf);
        responseBuf.flip();
        var connAck = (ConnAckPacket) codec.decode(responseBuf);
        assertThat(connAck.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);

        // When: send PINGREQ periodically
        Thread.sleep(1500);
        var ping = new PingReqPacket();
        transports[1].send(codec.encode(ping));

        // Read PINGRESP
        responseBuf.clear();
        bytesRead = transports[1].receive(responseBuf);
        assertThat(bytesRead).isGreaterThan(0);
        responseBuf.flip();
        var pingResp = codec.decode(responseBuf);
        assertThat(pingResp).isInstanceOf(PingRespPacket.class);

        // Then: client still connected after timeout window
        assertThat(broker.getConnectedClients()).contains("keepalive-ping");
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testZeroKeepAliveDisablesTimeout() throws Exception {
        // Given: connect with keep-alive=0 (disabled)
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);

        var codec = new MqttCodec(MqttVersion.V3_1_1);
        var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-zero", true, 0,
                null, null, null, new MqttProperties());
        transports[1].send(codec.encode(connect));

        // Read CONNACK
        var responseBuf = ByteBuffer.allocate(4096);
        int bytesRead = transports[1].receive(responseBuf);
        responseBuf.flip();
        var connAck = (ConnAckPacket) codec.decode(responseBuf);
        assertThat(connAck.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);

        // Then: stays connected even after long idle period
        Thread.sleep(3000);
        assertThat(broker.getConnectedClients()).contains("keepalive-zero");
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testKeepAliveTimeoutPublishesWill() throws Exception {
        // Given: subscribe to will topic on a separate transport
        var received = new CopyOnWriteArrayList<String>();
        var latch = new CountDownLatch(1);
        var subTransports = InMemoryMqttTransport.createPair();
        broker.handleConnection(subTransports[0]);

        try (var subClient = createClient("will-sub")) {
            subClient.connect().get(5, TimeUnit.SECONDS);
            subClient.subscribe("will/topic", QoS.AT_LEAST_ONCE, (t, p, q, r) -> {
                received.add(new String(p, StandardCharsets.UTF_8));
                latch.countDown();
            }).get(5, TimeUnit.SECONDS);
        }

        // Given: client with will and 1-second keep-alive (broker timeout is 1.5s)
        var will = new WillMessage("will/topic", "i-died".getBytes(), QoS.AT_LEAST_ONCE, false);
        var clientTransports = InMemoryMqttTransport.createPair();
        broker.handleConnection(clientTransports[0]);

        var codec = new MqttCodec(MqttVersion.V5_0);
        var connect = new ConnectPacket(MqttVersion.V5_0, "will-timeout", true, 1,
                null, null, will, new MqttProperties());
        clientTransports[1].send(codec.encode(connect));

        // Read CONNACK
        var responseBuf = ByteBuffer.allocate(4096);
        int bytesRead = clientTransports[1].receive(responseBuf);
        assertThat(bytesRead).isGreaterThan(0);
        responseBuf.flip();
        var connAck = (ConnAckPacket) codec.decode(responseBuf);
        assertThat(connAck.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);

        // Then: after timeout, will is published to subscriber
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(received).contains("i-died");
    }

    private MqttClient createClient(String clientId) {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var config = MqttClientConfig.defaults()
                .clientId(clientId).build();
        return new MqttClient(config, transports[1]);
    }
}
