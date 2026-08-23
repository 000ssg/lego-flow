package ssg.legoflow.messaging.amqp091.client;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Debug test: connect to RabbitMQ using our custom Amqp091Client.
 */
class Amqp091ClientDebugTest {

    private static final Logger LOG = LoggerFactory.getLogger(Amqp091ClientDebugTest.class);

    @Test
    void testConnect() throws Exception {
        String host = System.getProperty("amqp.debug.host", "localhost");
        int port = Integer.parseInt(System.getProperty("amqp.debug.port", "5672"));

        ClientConfig config = ClientConfig.builder()
                .host(host)
                .port(port)
                .username("guest")
                .password("guest")
                .build();

        try (var client = Amqp091Client.fromConfig(config)) {
            client.connect();
            assertThat(client.isConnected()).isTrue();
            LOG.info("SUCCESS: Connected to RabbitMQ at {}:{}", host, port);
        }
    }
}
