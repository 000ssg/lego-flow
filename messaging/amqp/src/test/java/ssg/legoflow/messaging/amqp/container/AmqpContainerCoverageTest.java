package ssg.legoflow.messaging.amqp.container;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

/**
 * AMQP container coverage tests to increase test coverage.
 */
class AmqpContainerCoverageTest {

    private AmqpContainer container;
    private int port;

    @BeforeEach
    void startContainer() throws Exception {
        var config = ContainerConfig.defaults();
        container = new AmqpContainer(config);
        container.start();
        port = container.port();
    }

    @AfterEach
    void stopContainer() throws Exception {
        if (container != null) container.close();
    }

    @Test void testIsRunning() {
        assertThat(container.isRunning()).isTrue();
    }

    @Test void testPortIsBound() {
        assertThat(port).isGreaterThan(0);
    }

    @Test void testContainerStartsAndStops() throws Exception {
        var config = ContainerConfig.defaults();
        var c = new AmqpContainer(config);
        assertThat(c.isRunning()).isFalse();
        c.start();
        assertThat(c.isRunning()).isTrue();
        c.close();
        assertThat(c.isRunning()).isFalse();
    }

    @Test void testCloseWithoutStartDoesNotThrow() throws Exception {
        var config = ContainerConfig.defaults();
        var c = new AmqpContainer(config);
        assertThatCode(c::close).doesNotThrowAnyException();
    }

    @Test void testNullConfigThrows() {
        assertThatThrownBy(() -> new AmqpContainer(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test void testDefaultsReturnsNonNull() {
        var config = ContainerConfig.defaults();
        assertThat(config).isNotNull();
    }

    @Test void testWithSaslReturnsNonNull() {
        var config = ContainerConfig.withSasl(null); // null is accepted
        assertThat(config).isNotNull();
    }
}
