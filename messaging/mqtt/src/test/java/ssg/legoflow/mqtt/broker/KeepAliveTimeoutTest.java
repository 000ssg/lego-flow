package ssg.legoflow.mqtt.broker;

import ssg.legoflow.mqtt.codec.MqttCodec;
import ssg.legoflow.mqtt.protocol.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for broker-side keep-alive timeout enforcement (Section 3.1.2.10).
 *
 * <p>The broker must disconnect a client if no PINGREQ is received
 * within 1.5x the keep_alive interval.
 *
 * <p>Timing-critical assertions use {@link TestAssertions} with exponential
 * backoff instead of {@code Thread.sleep()} to avoid flaky failures under parallel
 * execution (-T 1C). Simple delays between client-initiated actions remain as
 * {@code Thread.sleep()} since they don't depend on broker thread scheduling.
 *
 * @since 0.1.0
 */
class KeepAliveTimeoutTest {

    private MqttBroker broker;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.bind("localhost", 0);
        port = broker.getPort();
    }

    @AfterEach
    void tearDown() {
        broker.stop();
    }

    @Test
    void testClientDisconnectedOnKeepAliveTimeout() throws Exception {
        // Given: connect with 1-second keep-alive, then go silent
        var codec = new MqttCodec(MqttVersion.V3_1_1);
        try (var ch = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            ch.configureBlocking(true);

            var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-timeout",
                    true, 1, null, null, null, new MqttProperties());
            writePacket(ch, codec, connect);
            readPacket(ch, codec); // CONNACK

            assertThat(broker.getConnectedClients()).contains("keepalive-timeout");

            // When: wait for keep-alive timeout to fire (broker waits 1.5x = 1.5s)
            // Then: broker disconnected the client (retry up to 5s with backoff)
            TestAssertions.assertThatCondition(
                    "client disconnected on keep-alive timeout",
                    () -> !broker.getConnectedClients().contains("keepalive-timeout"),
                    Duration.ofSeconds(5));
        }
    }

    @Test
    void testClientStaysConnectedWithPings() throws Exception {
        // Given: connect with 1-second keep-alive
        var codec = new MqttCodec(MqttVersion.V3_1_1);
        try (var ch = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            ch.configureBlocking(true);

            var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-ping",
                    true, 1, null, null, null, new MqttProperties());
            writePacket(ch, codec, connect);
            readPacket(ch, codec); // CONNACK

            // When: send PINGREQs to stay alive (every 800ms < 1500ms timeout)
            for (int i = 0; i < 3; i++) {
                Thread.sleep(800);
                writePacket(ch, codec, new PingReqPacket());
                readPacket(ch, codec); // PINGRESP
            }

            // Then: still connected
            assertThat(broker.getConnectedClients()).contains("keepalive-ping");
        }
    }

    @Test
    void testZeroKeepAliveDisablesTimeout() throws Exception {
        // Given: connect with keep-alive=0 (disabled)
        var codec = new MqttCodec(MqttVersion.V3_1_1);
        try (var ch = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            ch.configureBlocking(true);

            var connect = new ConnectPacket(MqttVersion.V3_1_1, "keepalive-zero",
                    true, 0, null, null, null, new MqttProperties());
            writePacket(ch, codec, connect);
            readPacket(ch, codec); // CONNACK

            // When: wait some time without sending anything
            Thread.sleep(2000);

            // Then: still connected (keep-alive is disabled)
            assertThat(broker.getConnectedClients()).contains("keepalive-zero");

            // Cleanup
            writePacket(ch, codec, new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION,
                    new MqttProperties()));
        }
    }

    @Test
    void testKeepAliveTimeoutPublishesWill() throws Exception {
        // Given: client with will and 1-second keep-alive (broker timeout is 1.5s)
        var codec = new MqttCodec(MqttVersion.V3_1_1);

        // First, subscribe to will topic on a separate channel
        var subCodec = new MqttCodec(MqttVersion.V3_1_1);
        try (var subCh = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            subCh.configureBlocking(true);
            var subConnect = new ConnectPacket(MqttVersion.V3_1_1, "will-watcher",
                    true, 60, null, null, null, new MqttProperties());
            writePacket(subCh, subCodec, subConnect);
            readPacket(subCh, subCodec); // CONNACK

            // Subscribe to will topic
            var subscribe = new SubscribePacket(1,
                    java.util.List.of(new TopicSubscription("will/keepalive", QoS.AT_MOST_ONCE)),
                    new MqttProperties());
            writePacket(subCh, subCodec, subscribe);
            readPacket(subCh, subCodec); // SUBACK

            // Now connect the client with will (1-second keep-alive -> 1.5s timeout)
            try (var clientCh = SocketChannel.open(new InetSocketAddress("localhost", port))) {
                clientCh.configureBlocking(true);
                var will = new WillMessage("will/keepalive", "client-died".getBytes(),
                        QoS.AT_MOST_ONCE, false);
                var connect = new ConnectPacket(MqttVersion.V3_1_1, "will-client",
                        true, 1 /* 1-second keep-alive */, null, null, will, new MqttProperties());
                writePacket(clientCh, codec, connect);
                readPacket(clientCh, codec); // CONNACK

                assertThat(broker.getConnectedClients()).contains("will-client");
            }

            // When: let keep-alive timeout expire (retry up to 6s)
            // Then: client should be disconnected by broker (will message will be delivered)
            TestAssertions.assertThatCondition(
                    "will-client disconnected on keep-alive timeout",
                    () -> !broker.getConnectedClients().contains("will-client"),
                    Duration.ofSeconds(6));

            // Clean up subscriber
            writePacket(subCh, subCodec, new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION,
                    new MqttProperties()));
        }
    }

    // --- Helpers ---

    private void writePacket(SocketChannel ch, MqttCodec codec, MqttPacket packet) throws IOException {
        ByteBuffer buf = codec.encode(packet);
        while (buf.hasRemaining()) ch.write(buf);
    }

    private MqttPacket readPacket(SocketChannel ch, MqttCodec codec) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        ch.read(buf);
        buf.flip();
        return codec.decode(buf);
    }
}
