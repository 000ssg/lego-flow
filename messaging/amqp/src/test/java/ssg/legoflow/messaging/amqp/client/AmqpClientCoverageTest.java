package ssg.legoflow.messaging.amqp.client;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * AMQP client coverage tests to increase test coverage.
 */
class AmqpClientCoverageTest {

    @Test void testNullConfigThrows() {
        assertThatThrownBy(() -> new AmqpClient(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test void testConstructorWithConfigDoesNotThrow() {
        var config = ClientConfig.localhost(5672);
        assertThatCode(() -> new AmqpClient(config)).doesNotThrowAnyException();
    }

    @Test void testLocalhostReturnsNonNull() {
        var config = ClientConfig.localhost(5672);
        assertThat(config).isNotNull();
        assertThat(config.host()).isEqualTo("localhost");
    }

    @Test void testBuilderBasicSetup() {
        var config = ClientConfig.builder()
            .host("myhost")
            .port(1234)
            .build();
        assertThat(config).isNotNull();
        assertThat(config.host()).isEqualTo("myhost");
        assertThat(config.port()).isEqualTo(1234);
    }

    @Test void testBuilderWithContainerId() {
        var config = ClientConfig.builder()
            .host("localhost")
            .port(5672)
            .containerId("test-container")
            .build();
        assertThat(config.containerId()).isEqualTo("test-container");
    }

    @Test void testClientCloseDoesNotThrow() throws Exception {
        var config = ClientConfig.localhost(5672);
        var client = new AmqpClient(config);
        assertThatCode(client::close).doesNotThrowAnyException();
    }

    @Test void testBuilderDefaults() {
        var config = ClientConfig.builder().build();
        assertThat(config.host()).isEqualTo("localhost");
        assertThat(config.port()).isEqualTo(5672);
    }

    @Test void testLocalhostPortMapping() {
        var config = ClientConfig.localhost(0);
        assertThat(config.port()).isZero();
    }
}
