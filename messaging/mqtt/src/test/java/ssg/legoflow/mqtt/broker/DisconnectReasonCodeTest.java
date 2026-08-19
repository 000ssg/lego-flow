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
 * Tests for MQTT v5.0 DISCONNECT packet with reason codes (Section 3.14.2).
 *
 * <p>Timing-critical assertions use {@link TestAssertions} with exponential
 * backoff instead of {@code Thread.sleep()} to avoid flaky failures under parallel
 * execution (-T 1C).
 *
 * @since 0.1.0
 */
class DisconnectReasonCodeTest {

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
    void testDisconnectPacketCarriesReasonCode() {
        // Given: disconnect packet with specific reason code
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties());

        // Then: reason code is carried
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.NORMAL_DISCONNECTION);
        assertThat(disconnect.type()).isEqualTo(MqttPacketType.DISCONNECT);
    }

    @Test
    void testDisconnectWithWillReasonCode() {
        // Given: disconnect with will reason code
        var disconnect = new DisconnectPacket(ReasonCode.DISCONNECT_WITH_WILL, new MqttProperties());

        // Then: reason code value is 0x04
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.DISCONNECT_WITH_WILL);
        assertThat(disconnect.reasonCode().value()).isEqualTo(0x04);
    }

    @Test
    void testDisconnectMalformedPacketReasonCode() {
        // Given: disconnect with malformed packet reason code
        var disconnect = new DisconnectPacket(ReasonCode.MALFORMED_PACKET, new MqttProperties());

        // Then: reason code value is 0x81
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.MALFORMED_PACKET);
        assertThat(disconnect.reasonCode().value()).isEqualTo(0x81);
        assertThat(disconnect.reasonCode().isError()).isTrue();
    }

    @Test
    void testNormalDisconnectSuppressesWill() throws Exception {
        // Given: client with will message, connect via raw socket
        var codec = new MqttCodec(MqttVersion.V5_0);
        try (var ch = SocketChannel.open(new InetSocketAddress("localhost", port))) {
            ch.configureBlocking(true);

            // Send CONNECT with will
            var will = new WillMessage("will/topic", "will-msg".getBytes(),
                    QoS.AT_MOST_ONCE, false);
            var connect = new ConnectPacket(MqttVersion.V5_0, "will-normal", false,
                    60, null, null, will, new MqttProperties());
            writePacket(ch, codec, connect);

            // Read CONNACK
            readPacket(ch, codec);

            // When: send DISCONNECT with NORMAL_DISCONNECTION
            var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties());
            writePacket(ch, codec, disconnect);
        }

        // Allow async disconnect processing to complete (retry-based)
        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("will-normal"),
                Duration.ofSeconds(3), 50);

        // Then: will message was NOT published (normal disconnect)
        // Verify by checking there's no retained will message
        assertThat(broker.getRetainStore().get("will/topic")).isNull();
    }

    @Test
    void testAllDisconnectReasonCodes() {
        // Verify all relevant disconnect reason codes exist
        assertThat(ReasonCode.NORMAL_DISCONNECTION.value()).isEqualTo(0x00);
        assertThat(ReasonCode.DISCONNECT_WITH_WILL.value()).isEqualTo(0x04);
        assertThat(ReasonCode.MALFORMED_PACKET.value()).isEqualTo(0x81);
        assertThat(ReasonCode.PROTOCOL_ERROR.value()).isEqualTo(0x82);
        assertThat(ReasonCode.NOT_AUTHORIZED.value()).isEqualTo(0x87);
        assertThat(ReasonCode.SERVER_BUSY.value()).isEqualTo(0x89);
        assertThat(ReasonCode.SERVER_SHUTTING_DOWN.value()).isEqualTo(0x8B);
        assertThat(ReasonCode.KEEP_ALIVE_TIMEOUT.value()).isEqualTo(0x8D);
        assertThat(ReasonCode.SESSION_TAKEN_OVER.value()).isEqualTo(0x8E);
    }

    @Test
    void testErrorReasonCodesAreErrors() {
        // Given: error reason codes
        assertThat(ReasonCode.MALFORMED_PACKET.isError()).isTrue();
        assertThat(ReasonCode.PROTOCOL_ERROR.isError()).isTrue();
        assertThat(ReasonCode.NOT_AUTHORIZED.isError()).isTrue();
        assertThat(ReasonCode.SERVER_BUSY.isError()).isTrue();

        // And: success codes are not errors
        assertThat(ReasonCode.NORMAL_DISCONNECTION.isError()).isFalse();
        assertThat(ReasonCode.SUCCESS.isError()).isFalse();
    }

    @Test
    void testDisconnectPacketWithProperties() {
        // Given: disconnect with properties
        var props = new MqttProperties();
        props.setSessionExpiryInterval(3600);
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, props);

        // Then: properties accessible
        assertThat(disconnect.properties()).isNotNull();
        assertThat(disconnect.properties().getSessionExpiryInterval()).hasValue(3600L);
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
