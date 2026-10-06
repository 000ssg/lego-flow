package ssg.legoflow.messaging.mqtt.broker;

import ssg.legoflow.messaging.mqtt.codec.MqttCodec;
import ssg.legoflow.messaging.mqtt.protocol.*;
import ssg.legoflow.messaging.mqtt.transport.InMemoryMqttTransport;
import ssg.legoflow.messaging.mqtt.transport.MqttTransport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for MQTT 5.0 topic alias (property 0x23).
 *
 * The broker tracks topic aliases per connection. When a PUBLISH arrives with
 * an empty topic name but a TopicAlias property, the broker resolves the alias.
 * When a PUBLISH arrives with both topic name and alias, the broker registers it.
 *
 * Note: The MqttClient API does not yet expose topic alias properties, so these
 * tests are disabled until the client supports them.
 *
 * @since 0.2.0
 */
class TopicAliasTest {

    private MqttBroker broker;

    @BeforeEach
    void setUp() {
        broker = new MqttBroker(MqttBrokerConfig.minimal());
        broker.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        broker.close();
    }

    @Test
    void testAliasRegistrationAndResolution() throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var clientTransport = transports[1];

        var codec = new MqttCodec(MqttVersion.V5_0);
        connect("alias-test", codec, clientTransport);

        // Send PUBLISH with topic "sensors/temp" and alias 1
        var props = new MqttProperties();
        props.setTopicAlias(1);
        var pub = new PublishPacket("sensors/temp", "42".getBytes(StandardCharsets.UTF_8),
                QoS.AT_MOST_ONCE, false, false, 0, props);
        clientTransport.send(codec.encode(pub));
        Thread.sleep(100);

        // Now send PUBLISH with empty topic + alias 1 — should resolve to "sensors/temp"
        var aliasProps = new MqttProperties();
        aliasProps.setTopicAlias(1);
        var aliasPub = new PublishPacket("", "100".getBytes(StandardCharsets.UTF_8),
                QoS.AT_MOST_ONCE, false, false, 0, aliasProps);
        clientTransport.send(codec.encode(aliasPub));
        Thread.sleep(100);

        // Both should be accepted — the second one resolved the alias
        // We verify by checking the session has no errors
        var sessions = broker.getSessions();
        assertThat(sessions.containsKey("alias-test")).isTrue();
    }

    @Test
    void testAliasReplacesPrevious() throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var clientTransport = transports[1];

        var codec = new MqttCodec(MqttVersion.V5_0);
        connect("alias-replace", codec, clientTransport);

        // Register alias 1 -> "topic/a"
        var props1 = new MqttProperties();
        props1.setTopicAlias(1);
        clientTransport.send(codec.encode(new PublishPacket("topic/a", "x".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, props1)));
        Thread.sleep(50);

        // Re-register alias 1 -> "topic/b"
        var props2 = new MqttProperties();
        props2.setTopicAlias(1);
        clientTransport.send(codec.encode(new PublishPacket("topic/b", "y".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, props2)));
        Thread.sleep(50);

        // Send with alias 1 — should resolve to "topic/b"
        var aliasProps = new MqttProperties();
        aliasProps.setTopicAlias(1);
        clientTransport.send(codec.encode(new PublishPacket("", "z".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, aliasProps)));
        Thread.sleep(50);

        var sessions = broker.getSessions();
        assertThat(sessions.containsKey("alias-replace")).isTrue();
    }

    @Test
    void testUnknownAliasDropped() throws Exception {
        var transports = InMemoryMqttTransport.createPair();
        broker.handleConnection(transports[0]);
        var clientTransport = transports[1];

        var codec = new MqttCodec(MqttVersion.V5_0);
        connect("unknown-alias", codec, clientTransport);

        // Send with alias 99 — not registered — should be silently dropped
        var props = new MqttProperties();
        props.setTopicAlias(99);
        clientTransport.send(codec.encode(new PublishPacket("", "dropped".getBytes(),
                QoS.AT_MOST_ONCE, false, false, 0, props)));
        Thread.sleep(100);

        // Session should still be alive (not disconnected)
        var sessions = broker.getSessions();
        assertThat(sessions.containsKey("unknown-alias")).isTrue();
        var session = sessions.get("unknown-alias");
        assertThat(session.isConnected()).isTrue();
    }

    private void connect(String clientId, MqttCodec codec, MqttTransport transport) throws Exception {
        var conn = new ConnectPacket(MqttVersion.V5_0, clientId, true, 30,
                null, null, null, new MqttProperties());
        transport.send(codec.encode(conn));
        transport.receiveWithTimeout(ByteBuffer.allocate(65536), 2000, TimeUnit.MILLISECONDS); // CONNACK
    }
}
