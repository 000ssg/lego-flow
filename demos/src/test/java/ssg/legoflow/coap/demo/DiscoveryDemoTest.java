package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * Tests for {@link DiscoveryDemo}.
 *
 * @since 0.1.0
 */
class DiscoveryDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;

    /** Timeout for each test to prevent hangs under load. */
    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(10);

    private DiscoveryDemo demo;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        demo = new DiscoveryDemo(PORT);
        demo.start();
        // Allow the UDP socket and receive thread to settle under heavy build load
        Thread.sleep(100);
    }

    @AfterEach
    void tearDown() {
        if (demo != null) demo.stop();
    }

    @Test
    void testDiscoverAllResources() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            var request = CoapMessage.builder()
                    .type(CoapType.CONFIRMABLE)
                    .code(CoapCode.GET)
                    .messageId(1)
                    .uriPath("/.well-known/core")
                    .build();

            var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));

            assertThat(response).as("discovery response should not be null").isNotNull();
            assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
            var payload = response.getPayloadString();
            assertThat(payload).contains("/sensors/temperature");
            assertThat(payload).contains("/sensors/humidity");
            assertThat(payload).contains("/sensors/pressure");
            assertThat(payload).contains("/actuators/light");
        });
    }

    @Test
    void testDiscoverByType() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            var request = CoapMessage.builder()
                    .type(CoapType.CONFIRMABLE)
                    .code(CoapCode.GET)
                    .messageId(2)
                    .uriPath("/.well-known/core")
                    .uriQuery("rt=temperature")
                    .build();

            var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));

            assertThat(response).as("discovery response should not be null").isNotNull();
            var payload = response.getPayloadString();
            assertThat(payload).contains("/sensors/temperature");
            assertThat(payload).doesNotContain("/sensors/humidity");
        });
    }

    @Test
    void testDiscoverByInterface() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            var request = CoapMessage.builder()
                    .type(CoapType.CONFIRMABLE)
                    .code(CoapCode.GET)
                    .messageId(3)
                    .uriPath("/.well-known/core")
                    .uriQuery("if=actuator")
                    .build();

            var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));

            assertThat(response).as("discovery response should not be null").isNotNull();
            var payload = response.getPayloadString();
            assertThat(payload).contains("/actuators/light");
            assertThat(payload).doesNotContain("/sensors");
        });
    }

    @Test
    void testGetSensorValue() {
        assertTimeoutPreemptively(TEST_TIMEOUT, () -> {
            var request = CoapMessage.builder()
                    .type(CoapType.CONFIRMABLE)
                    .code(CoapCode.GET)
                    .messageId(4)
                    .uriPath("/sensors/temperature")
                    .build();

            var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));

            assertThat(response).as("sensor response should not be null").isNotNull();
            assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
            assertThat(response.getPayloadString()).contains("temperature");
        });
    }
}
