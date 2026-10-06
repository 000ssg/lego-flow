package ssg.legoflow.messaging.mqtt.client;

import ssg.legoflow.messaging.mqtt.protocol.MqttVersion;
import ssg.legoflow.messaging.mqtt.protocol.QoS;
import ssg.legoflow.messaging.mqtt.protocol.WillMessage;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Tests for {@link MqttClientConfig}.
 *
 * @since 0.1.0
 */
class MqttClientConfigTest {

    @Test
    void testDefaultConfig() {
        // Given/When: default builder
        var config = MqttClientConfig.defaults().build();

        // Then: defaults applied
        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(1883);
        assertThat(config.version()).isEqualTo(MqttVersion.V3_1_1);
        assertThat(config.cleanSession()).isTrue();
        assertThat(config.keepAlive()).isEqualTo(60);
        assertThat(config.autoReconnect()).isFalse();
        assertThat(config.username()).isNull();
        assertThat(config.password()).isNull();
    }

    @Test
    void testCustomConfig() {
        // Given/When: fully customized config
        var config = MqttClientConfig.defaults()
                .host("broker.example.com")
                .port(8883)
                .clientId("my-client")
                .version(MqttVersion.V5_0)
                .cleanSession(false)
                .username("user")
                .password("pass")
                .keepAlive(120)
                .autoReconnect(true)
                .reconnectDelay(Duration.ofSeconds(10))
                .maxInflightMessages(20)
                .build();

        // Then: all values set
        assertThat(config.host()).isEqualTo("broker.example.com");
        assertThat(config.port()).isEqualTo(8883);
        assertThat(config.clientId()).isEqualTo("my-client");
        assertThat(config.version()).isEqualTo(MqttVersion.V5_0);
        assertThat(config.cleanSession()).isFalse();
        assertThat(config.autoReconnect()).isTrue();
        assertThat(config.maxInflightMessages()).isEqualTo(20);
    }

    @Test
    void testWithCleanSessionFactory() {
        // Given/When: factory method
        var config = MqttClientConfig.withCleanSession("remote-host", 9883);

        // Then: clean session with specified host/port
        assertThat(config.host()).isEqualTo("remote-host");
        assertThat(config.port()).isEqualTo(9883);
        assertThat(config.cleanSession()).isTrue();
    }

    @Test
    void testInvalidPortThrows() {
        // Given/When/Then: invalid port
        assertThatThrownBy(() -> MqttClientConfig.defaults().port(0).build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MqttClientConfig.defaults().port(70000).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWillMessage() {
        // Given: config with will
        var will = new WillMessage("status", "offline".getBytes(), QoS.AT_LEAST_ONCE, true);
        var config = MqttClientConfig.defaults().will(will).build();

        // Then: will is set
        assertThat(config.will()).isNotNull();
        assertThat(config.will().topic()).isEqualTo("status");
    }

    @Test
    void testClientIdGenerated() {
        // Given/When: two default configs
        var c1 = MqttClientConfig.defaults().build();
        var c2 = MqttClientConfig.defaults().build();

        // Then: different auto-generated client IDs
        assertThat(c1.clientId()).isNotEqualTo(c2.clientId());
        assertThat(c1.clientId()).startsWith("lego-flow-");
    }
}
