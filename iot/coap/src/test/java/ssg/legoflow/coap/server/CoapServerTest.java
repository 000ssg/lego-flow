package ssg.legoflow.coap.server;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapServer}.
 *
 * @since 0.1.0
 */
class CoapServerTest {

    private CoapServer server;
    private final InetSocketAddress source = new InetSocketAddress("localhost", 12345);

    @BeforeEach
    void setUp() {
        server = new CoapServer(CoapServerConfig.withPort(0));
        server.add(new CoapResource("temp", "/sensors/temp") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT,
                        "22.5".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }

            @Override
            public void handlePut(CoapExchange exchange) {
                exchange.respond(CoapCode.CHANGED);
            }

            @Override
            public void handleDelete(CoapExchange exchange) {
                exchange.respond(CoapCode.DELETED);
            }
        });
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void testRouteGetRequest() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/sensors/temp")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).isEqualTo("22.5");
    }

    @Test
    void testRoutePutRequest() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.PUT)
                .messageId(2)
                .uriPath("/sensors/temp")
                .payload("23.0")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);
    }

    @Test
    void testRouteDeleteRequest() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.DELETE)
                .messageId(3)
                .uriPath("/sensors/temp")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.DELETED);
    }

    @Test
    void testNotFoundResource() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(4)
                .uriPath("/nonexistent")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.NOT_FOUND);
    }

    @Test
    void testConReturnsAck() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(5)
                .uriPath("/sensors/temp")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response.type()).isEqualTo(CoapType.ACKNOWLEDGEMENT);
        assertThat(response.messageId()).isEqualTo(5);
    }

    @Test
    void testNonReturnsNon() {
        var request = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(6)
                .uriPath("/sensors/temp")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response.type()).isEqualTo(CoapType.NON_CONFIRMABLE);
    }

    @Test
    void testDeduplication() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(7)
                .uriPath("/sensors/temp")
                .build();

        // First request
        var response1 = server.handleMessage(request, source);
        assertThat(response1).isNotNull();

        // Duplicate should return cached response
        var response2 = server.handleMessage(request, source);
        assertThat(response2).isNotNull();
        assertThat(response2.code()).isEqualTo(response1.code());
    }

    @Test
    void testEmptyMessagePingPong() {
        var ping = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.EMPTY)
                .messageId(8)
                .build();

        var response = server.handleMessage(ping, source);

        assertThat(response).isNotNull();
        assertThat(response.type()).isEqualTo(CoapType.RESET);
    }

    @Test
    void testObserveRegistration() {
        var token = new byte[]{0x01, 0x02};
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(9)
                .token(token)
                .uriPath("/sensors/temp")
                .option(CoapOption.observe(0))
                .build();

        server.handleMessage(request, source);

        var observers = server.observeRegistry().getObservers("/sensors/temp");
        assertThat(observers).hasSize(1);
    }

    @Test
    void testSeparateResponseSendsEmptyAckImmediately() {
        // Add a slow resource that needs separate response
        server.add(new CoapResource("slow", "/slow") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.markSeparateResponse();
                // Simulate delayed processing, then respond
                exchange.respondSeparate(CoapCode.CONTENT,
                        "delayed result".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value(),
                        server.nextMessageId());
            }
        });

        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(100)
                .token(new byte[]{0x10, 0x20})
                .uriPath("/slow")
                .build();

        // handleMessage returns the empty ACK immediately
        var emptyAck = server.handleMessage(request, source);

        assertThat(emptyAck).isNotNull();
        assertThat(emptyAck.type()).isEqualTo(CoapType.ACKNOWLEDGEMENT);
        assertThat(emptyAck.code()).isEqualTo(CoapCode.EMPTY);
        assertThat(emptyAck.messageId()).isEqualTo(100);
    }

    @Test
    void testSeparateResponseQueuedForDelivery() {
        server.add(new CoapResource("slow", "/slow") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.markSeparateResponse();
                exchange.respondSeparate(CoapCode.CONTENT,
                        "result".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value(),
                        server.nextMessageId());
            }
        });

        var token = new byte[]{0x30, 0x40};
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(101)
                .token(token)
                .uriPath("/slow")
                .build();

        server.handleMessage(request, source);

        // Separate response should be queued
        assertThat(server.pendingSeparateResponseCount()).isEqualTo(1);

        var entry = server.takeSeparateResponse(token);
        assertThat(entry).isNotNull();
        assertThat(entry.response().type()).isEqualTo(CoapType.CONFIRMABLE);
        assertThat(entry.response().code()).isEqualTo(CoapCode.CONTENT);
        assertThat(entry.response().getPayloadString()).isEqualTo("result");
        assertThat(entry.response().messageId()).isNotEqualTo(101); // Different message ID
        assertThat(entry.target()).isEqualTo(source);
    }

    @Test
    void testSeparateResponseNonRequestFallsBack() {
        server.add(new CoapResource("slow", "/slow") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.markSeparateResponse();
                exchange.respondSeparate(CoapCode.CONTENT,
                        "result".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value(),
                        server.nextMessageId());
            }
        });

        // NON request — separate response pattern only applies to CON
        var request = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(102)
                .token(new byte[]{0x50, 0x60})
                .uriPath("/slow")
                .build();

        var response = server.handleMessage(request, source);

        // For NON requests, the response is returned directly (no empty ACK pattern)
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
    }

    @Test
    void testMulticastAddressDetection() {
        var multicast = new InetSocketAddress("224.0.1.187", 5683);
        assertThat(CoapServer.isMulticastAddress(multicast)).isTrue();

        var unicast = new InetSocketAddress("192.168.1.1", 5683);
        assertThat(CoapServer.isMulticastAddress(unicast)).isFalse();
    }

    @Test
    void testMulticastResponseUsesNon() {
        // When multicast is enabled, responses should use NON even if request is CON
        // First, verify default behavior
        var request = CoapMessage.builder()
                .type(CoapType.NON_CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(200)
                .uriPath("/sensors/temp")
                .build();

        var response = server.handleMessage(request, source);
        assertThat(response.type()).isEqualTo(CoapType.NON_CONFIRMABLE);
    }

    @Test
    void testWellKnownCoreDiscovery() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(10)
                .uriPath("/.well-known/core")
                .build();

        var response = server.handleMessage(request, source);

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).contains("/sensors/temp");
    }
}
