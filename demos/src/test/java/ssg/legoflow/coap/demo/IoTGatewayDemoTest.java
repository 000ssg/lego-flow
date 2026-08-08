package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link IoTGatewayDemo}.
 *
 * @since 0.1.0
 */
class IoTGatewayDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private IoTGatewayDemo demo;
    private final InetSocketAddress source = new InetSocketAddress("localhost", 12345);

    @BeforeEach
    void setUp() throws IOException {
        demo = new IoTGatewayDemo(PORT);
        demo.registerNode("sensor-1", "temperature", "22.5");
        demo.registerNode("sensor-2", "humidity", "65");
        demo.start();
    }

    @AfterEach
    void tearDown() {
        if (demo != null) demo.stop();
    }

    @Test
    void testGetSensorNode() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/nodes/sensor-1")
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).contains("sensor-1");
        assertThat(response.getPayloadString()).contains("22.5");
    }

    @Test
    void testUpdateSensorNode() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.PUT)
                .messageId(2)
                .uriPath("/nodes/sensor-1")
                .payload("25.0".getBytes(StandardCharsets.UTF_8))
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);
        assertThat(demo.sensorNodes().get("sensor-1").value()).isEqualTo("25.0");
    }

    @Test
    void testGatewayStatus() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(3)
                .uriPath("/gateway/status")
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).contains("sensor-1");
        assertThat(response.getPayloadString()).contains("sensor-2");
    }

    @Test
    void testUpdateNodeValue() {
        demo.updateNodeValue("sensor-1", "30.0");

        assertThat(demo.sensorNodes().get("sensor-1").value()).isEqualTo("30.0");
    }

    @Test
    void testDiscoverNodes() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(4)
                .uriPath("/.well-known/core")
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).contains("/nodes/sensor-1");
        assertThat(response.getPayloadString()).contains("/nodes/sensor-2");
    }
}
