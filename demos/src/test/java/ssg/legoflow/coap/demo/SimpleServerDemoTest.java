package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.client.CoapClient;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link SimpleServerDemo}.
 *
 * @since 0.1.0
 */
class SimpleServerDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private SimpleServerDemo demo;
    private CoapClient client;

    @BeforeEach
    void setUp() throws IOException {
        demo = new SimpleServerDemo(PORT);
        demo.start();
        client = new CoapClient("localhost", demo.server().getPort());
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (demo != null) demo.stop();
    }

    @Test
    void testGetTemperature() throws IOException {
        var response = client.get("/sensors/temperature");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPayloadString()).isEqualTo("22.5");
    }

    @Test
    void testGetHumidity() throws IOException {
        var response = client.get("/sensors/humidity");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPayloadString()).isEqualTo("65");
    }

    @Test
    void testPutTemperature() throws IOException {
        var response = client.put("/sensors/temperature",
                "25.0".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());

        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);

        // Verify updated
        var getResponse = client.get("/sensors/temperature");
        assertThat(getResponse.getPayloadString()).isEqualTo("25.0");
    }

    @Test
    void testPutHumidity() throws IOException {
        var response = client.put("/sensors/humidity",
                "70".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());

        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);
    }

    @Test
    void testServerHasResources() {
        assertThat(demo.server().getResource("/sensors/temperature")).isNotNull();
        assertThat(demo.server().getResource("/sensors/humidity")).isNotNull();
    }
}
