package ssg.legoflow.coap.resource;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.ContentFormat;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapResource}.
 *
 * @since 0.1.0
 */
class CoapResourceTest {

    @Test
    void testDefaultHandlerReturnsMethodNotAllowed() {
        var resource = new CoapResource("test", "/test");
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .build();
        var exchange = new CoapExchange(request, new InetSocketAddress(5683));

        resource.handleGet(exchange);

        assertThat(exchange.hasResponse()).isTrue();
        assertThat(exchange.getResponse().code()).isEqualTo(CoapCode.METHOD_NOT_ALLOWED);
    }

    @Test
    void testCustomGetHandler() {
        var resource = new CoapResource("temp", "/sensors/temp") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT,
                        "22.5".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }
        };

        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .build();
        var exchange = new CoapExchange(request, new InetSocketAddress(5683));

        resource.handleGet(exchange);

        assertThat(exchange.getResponse().code()).isEqualTo(CoapCode.CONTENT);
        assertThat(new String(exchange.getResponse().payload(), StandardCharsets.UTF_8)).isEqualTo("22.5");
    }

    @Test
    void testAddChild() {
        var parent = new CoapResource("sensors", "/sensors");
        var child = new CoapResource("temp", "/sensors/temp");

        parent.addChild(child);

        assertThat(parent.getChild("temp")).isNotNull();
        assertThat(parent.getChild("temp").path()).isEqualTo("/sensors/temp");
    }

    @Test
    void testGetChildNotFound() {
        var resource = new CoapResource("test", "/test");

        assertThat(resource.getChild("nonexistent")).isNull();
    }

    @Test
    void testAttributes() {
        var resource = new CoapResource("temp", "/sensors/temp", true);
        resource.getAttributes()
                .resourceType("temperature")
                .interfaceDescription("sensor")
                .contentFormat(ContentFormat.TEXT_PLAIN.value())
                .title("Temperature Sensor");

        var attrs = resource.getAttributes();
        assertThat(attrs.resourceType()).isEqualTo("temperature");
        assertThat(attrs.interfaceDescription()).isEqualTo("sensor");
        assertThat(attrs.contentFormat()).isEqualTo(ContentFormat.TEXT_PLAIN.value());
        assertThat(attrs.observable()).isTrue();
    }

    @Test
    void testObservableResource() {
        var resource = new CoapResource("temp", "/sensors/temp", true);

        assertThat(resource.isObservable()).isTrue();
    }

    @Test
    void testNotifyObservers() {
        var notified = new AtomicBoolean(false);
        var resource = new CoapResource("temp", "/sensors/temp", true);
        resource.setObserveNotifier(path -> notified.set(true));

        resource.notifyObservers();

        assertThat(notified.get()).isTrue();
    }

    @Test
    void testCoreLinkFormat() {
        var resource = new CoapResource("temp", "/sensors/temp", true);
        resource.getAttributes()
                .resourceType("temperature")
                .contentFormat(ContentFormat.TEXT_PLAIN.value());

        var linkFormat = resource.toCoreLinkFormat();

        assertThat(linkFormat).contains("</sensors/temp>");
        assertThat(linkFormat).contains("rt=\"temperature\"");
        assertThat(linkFormat).contains("obs");
    }
}
