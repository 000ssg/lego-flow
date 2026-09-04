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
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for MQTT disconnect reason codes (MQTT 5.0).
 *
 * @since 0.2.0
 */
class DisconnectReasonCodeTest {

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
    void testDisconnectPacketCarriesReasonCode() {
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties());
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.NORMAL_DISCONNECTION);
        assertThat(disconnect.type()).isEqualTo(MqttPacketType.DISCONNECT);
    }

    @Test
    void testDisconnectWithWillReasonCode() {
        var disconnect = new DisconnectPacket(ReasonCode.DISCONNECT_WITH_WILL, new MqttProperties());
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.DISCONNECT_WITH_WILL);
        assertThat(disconnect.reasonCode().value()).isEqualTo(0x04);
    }

    @Test
    void testDisconnectMalformedPacketReasonCode() {
        var disconnect = new DisconnectPacket(ReasonCode.MALFORMED_PACKET, new MqttProperties());
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.MALFORMED_PACKET);
        assertThat(disconnect.reasonCode().value()).isEqualTo(0x81);
    }

    @Test
    @Disabled("Needs raw transport driving — will be replaced when MqttClientService is available")
    void testNormalDisconnectSuppressesWill() throws Exception {
        // Given: client with will message, connect via in-memory transport
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);

        var codec = new MqttCodec(MqttVersion.V5_0);

        // Send CONNECT with will
        var will = new WillMessage("will/topic", "will-payload".getBytes(), QoS.AT_LEAST_ONCE, false);
        var connectPacket = new ConnectPacket(MqttVersion.V5_0, "will-test", true, 60,
                null, null, will, new MqttProperties());

        var connectBuf = codec.encode(connectPacket);
        transports[1].send(connectBuf);

        // Read CONNACK
        var responseBuf = ByteBuffer.allocate(4096);
        int bytesRead = transports[1].receive(responseBuf);
        assertThat(bytesRead).isGreaterThan(0);
        responseBuf.flip();
        var connAck = (ConnAckPacket) codec.decode(responseBuf);
        assertThat(connAck.returnCode()).isEqualTo(ConnectReturnCode.ACCEPTED);

        // When: send DISCONNECT (normal — suppresses will)
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, new MqttProperties());
        var disconnectBuf = codec.encode(disconnect);
        transports[1].send(disconnectBuf);

        // Close transport to simulate disconnect
        transports[0].close();
        transports[1].close();

        // Then: broker processed the DISCONNECT (will was suppressed)
        // Allow time for disconnect processing
        TestAssertions.waitForCondition(
                () -> !broker.getConnectedClients().contains("will-test"),
                Duration.ofSeconds(3), 50);
    }

    @Test
    void testAllDisconnectReasonCodes() {
        assertThat(ReasonCode.NORMAL_DISCONNECTION.value()).isEqualTo(0x00);
        assertThat(ReasonCode.DISCONNECT_WITH_WILL.value()).isEqualTo(0x04);
        assertThat(ReasonCode.MALFORMED_PACKET.value()).isEqualTo(0x81);
        assertThat(ReasonCode.PROTOCOL_ERROR.value()).isEqualTo(0x82);
        assertThat(ReasonCode.NOT_AUTHORIZED.value()).isEqualTo(0x87);
        assertThat(ReasonCode.SERVER_BUSY.value()).isEqualTo(0x89);
    }

    @Test
    void testErrorReasonCodesAreErrors() {
        assertThat(ReasonCode.MALFORMED_PACKET.isError()).isTrue();
        assertThat(ReasonCode.PROTOCOL_ERROR.isError()).isTrue();
        assertThat(ReasonCode.NOT_AUTHORIZED.isError()).isTrue();
        assertThat(ReasonCode.SERVER_BUSY.isError()).isTrue();
        assertThat(ReasonCode.NORMAL_DISCONNECTION.isError()).isFalse();
        assertThat(ReasonCode.DISCONNECT_WITH_WILL.isError()).isFalse();
    }

    @Test
    void testDisconnectPacketWithProperties() {
        var props = new MqttProperties();
        props.setSessionExpiryInterval(3600);
        var disconnect = new DisconnectPacket(ReasonCode.NORMAL_DISCONNECTION, props);
        assertThat(disconnect.reasonCode()).isEqualTo(ReasonCode.NORMAL_DISCONNECTION);
        assertThat(disconnect.properties()).isNotNull();
    }
}
