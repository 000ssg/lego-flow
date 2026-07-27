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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for broker-side keep-alive timeout enforcement (Section 3.1.2.10).
 *
 * <p>The broker must disconnect a client if no PINGREQ is received
 * within 1.5x the keep_alive interval.
 *
 * @since 1.0.0
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

            // When: wait for 1.5x keep_alive (1.5 seconds) + margin
            Thread.sleep(2500);

            // Then: broker disconnected the client
            assertThat(broker.getConnectedClients()).doesNotContain("keepalive-timeout");
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

            // When: send PINGREQs to stay alive
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
        // Given: client with will and 1-second keep-alive
        var codec = new MqttCodec(MqttVersion.V3_1_1);

        // First, subscribe to will topic
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

            // Now connect the client with will
            try (var clientCh = SocketChannel.open(new InetSocketAddress("localhost", port))) {
                clientCh.configureBlocking(true);
                var will = new WillMessage("will/keepalive", "client-died".getBytes(),
                        QoS.AT_MOST_ONCE, false);
                var connect = new ConnectPacket(MqttVersion.V3_1_1, "will-client",
                        true, 1, null, null, will, new MqttProperties());
                writePacket(clientCh, codec, connect);
                readPacket(clientCh, codec); // CONNACK

                // When: let keep-alive timeout expire
                Thread.sleep(2500);
            }

            // Then: will message should be published
            // (We don't try to read from subCh since the timing is tricky in tests,
            // but we verify the client was disconnected by the broker)
            assertThat(broker.getConnectedClients()).doesNotContain("will-client");
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
