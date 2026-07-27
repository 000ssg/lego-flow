package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WellKnownCoreResource}.
 *
 * @since 1.0.0
 */
class WellKnownCoreResourceTest {

    private List<CoapResource> resources;
    private WellKnownCoreResource wellKnown;

    @BeforeEach
    void setUp() {
        resources = new ArrayList<>();
        wellKnown = new WellKnownCoreResource(() -> resources);

        var temp = new CoapResource("temp", "/sensors/temp", true);
        temp.getAttributes().resourceType("temperature").interfaceDescription("sensor")
                .contentFormat(ContentFormat.TEXT_PLAIN.value());
        resources.add(temp);

        var humidity = new CoapResource("humidity", "/sensors/humidity");
        humidity.getAttributes().resourceType("humidity").interfaceDescription("sensor");
        resources.add(humidity);

        var light = new CoapResource("light", "/actuators/light");
        light.getAttributes().resourceType("light").interfaceDescription("actuator");
        resources.add(light);

        resources.add(wellKnown);
    }

    @Test
    void testReturnsLinkFormat() {
        var exchange = createExchange(null, null);
        wellKnown.handleGet(exchange);

        assertThat(exchange.getResponse().code()).isEqualTo(CoapCode.CONTENT);
        var payload = new String(exchange.getResponse().payload(), StandardCharsets.UTF_8);
        assertThat(payload).contains("</sensors/temp>");
        assertThat(payload).contains("</sensors/humidity>");
        assertThat(payload).contains("</actuators/light>");
    }

    @Test
    void testFilterByResourceType() {
        var exchange = createExchange("rt", "temperature");
        wellKnown.handleGet(exchange);

        var payload = new String(exchange.getResponse().payload(), StandardCharsets.UTF_8);
        assertThat(payload).contains("</sensors/temp>");
        assertThat(payload).doesNotContain("</sensors/humidity>");
        assertThat(payload).doesNotContain("</actuators/light>");
    }

    @Test
    void testFilterByInterface() {
        var exchange = createExchange("if", "actuator");
        wellKnown.handleGet(exchange);

        var payload = new String(exchange.getResponse().payload(), StandardCharsets.UTF_8);
        assertThat(payload).contains("</actuators/light>");
        assertThat(payload).doesNotContain("</sensors/temp>");
    }

    @Test
    void testIncludesAttributes() {
        var exchange = createExchange(null, null);
        wellKnown.handleGet(exchange);

        var payload = new String(exchange.getResponse().payload(), StandardCharsets.UTF_8);
        assertThat(payload).contains("rt=\"temperature\"");
        assertThat(payload).contains("obs");
    }

    @Test
    void testDoesNotListSelf() {
        var exchange = createExchange(null, null);
        wellKnown.handleGet(exchange);

        var payload = new String(exchange.getResponse().payload(), StandardCharsets.UTF_8);
        assertThat(payload).doesNotContain("/.well-known/core");
    }

    @Test
    void testContentFormatIsLinkFormat() {
        var exchange = createExchange(null, null);
        wellKnown.handleGet(exchange);

        assertThat(exchange.getResponse().getContentFormat())
                .isEqualTo(ContentFormat.APPLICATION_LINK_FORMAT.value());
    }

    private CoapExchange createExchange(String queryKey, String queryValue) {
        var builder = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/.well-known/core");

        if (queryKey != null && queryValue != null) {
            builder.uriQuery(queryKey + "=" + queryValue);
        }

        return new CoapExchange(builder.build(), new InetSocketAddress(5683));
    }
}
