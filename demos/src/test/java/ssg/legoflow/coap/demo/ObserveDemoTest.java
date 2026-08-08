package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.client.CoapClient;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ObserveDemo}.
 *
 * @since 0.1.0
 */
class ObserveDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private ObserveDemo demo;

    @BeforeEach
    void setUp() throws IOException {
        demo = new ObserveDemo(PORT);
    }

    @AfterEach
    void tearDown() {
        if (demo != null) demo.stop();
    }

    @Test
    void testObservableResourceCreated() {
        assertThat(demo.temperatureResource()).isNotNull();
        assertThat(demo.temperatureResource().isObservable()).isTrue();
    }

    @Test
    void testSetTemperature() {
        demo.setTemperature("30.5");

        assertThat(demo.temperatureResource().currentValue()).isEqualTo("30.5");
    }

    @Test
    void testObserveRegistration() throws IOException {
        demo.start(60_000); // Long interval so it doesn't fire during test

        // Simulate observe registration via server API
        var token = new byte[]{0x01, 0x02};
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .token(token)
                .uriPath("/sensors/temperature")
                .option(CoapOption.observe(0))
                .build();

        demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));

        var observers = demo.server().observeRegistry().getObservers("/sensors/temperature");
        assertThat(observers).hasSize(1);
    }

    @Test
    void testGetTemperatureDirectly() throws IOException {
        demo.start(60_000);

        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/sensors/temperature")
                .build();

        var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
    }
}
