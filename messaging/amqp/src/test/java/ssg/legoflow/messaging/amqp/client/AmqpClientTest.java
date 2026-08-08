package ssg.legoflow.messaging.amqp.client;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AmqpClientTest {
    @Test void testClientRejectsNullConfig() {
        assertThatThrownBy(() -> new AmqpClient(null)).isInstanceOf(NullPointerException.class);
    }
    @Test void testCloseNotConnected() throws Exception {
        var config = ClientConfig.builder().host("localhost").port(5672).build();
        try (var client = new AmqpClient(config)) {}
    }
    @Test void testClientConfigBuilder() {
        var config = ClientConfig.builder().host("h").port(999).build();
        assertThat(config.host()).isEqualTo("h");
        assertThat(config.port()).isEqualTo(999);
    }
    @Test void testClientConfigLocalhost() {
        var config = ClientConfig.localhost(5672);
        assertThat(config.port()).isEqualTo(5672);
    }
}
