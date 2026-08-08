package ssg.legoflow.coap.server;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

/**
 * Extended tests for {@link CoapServer} covering additional code paths:
 * POST handling, observe registration/deregistration, empty messages (ping),
 * RESET message handling, resource management, server lifecycle, and config.
 */
class CoapServerExtendedTest {

    private CoapServer server;
    private final InetSocketAddress source = new InetSocketAddress("localhost", 23456);

    @BeforeEach
    void setUp() {
        server = new CoapServer(CoapServerConfig.withPort(0));
        server.add(new CoapResource("data", "/data") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT, "value".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }

            @Override
            public void handlePost(CoapExchange exchange) {
                exchange.respond(CoapCode.CREATED);
            }

            @Override
            public void handlePut(CoapExchange exchange) {
                exchange.respond(CoapCode.CHANGED);
            }
        });
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    // ── POST handling ────────────────────────────────────────────────────────

    @Test
    void testPostRequestCreatesResource() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(201)
                .uriPath("/data")
                .payload("new-data")
                .build();

        var response = server.handleMessage(request, source);
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CREATED);
    }

    // ── Empty message (ping) -> RST response ────────────────────────────────

    @Test
    void testEmptyMessageReturnsRst() {
        var ping = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.EMPTY)
                .messageId(301)
                .build();

        var response = server.handleMessage(ping, source);
        assertThat(response).isNotNull();
        assertThat(response.type()).isEqualTo(CoapType.RESET);
        assertThat(response.code()).isEqualTo(CoapCode.EMPTY);
    }

    // ── RESET message handling (observe deregistration) ──────────────────────

    @Test
    void testResetMessageReturnsNull() {
        var reset = CoapMessage.builder()
                .type(CoapType.RESET)
                .code(CoapCode.EMPTY)
                .messageId(401)
                .token(new byte[]{(byte) 0xAA})
                .build();

        // RESET messages should be handled (observe deregistered) and return null
        var response = server.handleMessage(reset, source);
        assertThat(response).isNull();
    }

    // ── ACK message handling ────────────────────────────────────────────────

    @Test
    void testAckMessageReturnsNull() {
        var ack = CoapMessage.builder()
                .type(CoapType.ACKNOWLEDGEMENT)
                .code(CoapCode.EMPTY)
                .messageId(501)
                .build();

        var response = server.handleMessage(ack, source);
        assertThat(response).isNull();
    }

    // ── Observe registration (observe=0) ────────────────────────────────────

    @Test
    void testObserveRegistration() {
        var token = new byte[]{(byte) 0xBB};
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(601)
                .token(token)
                .uriPath("/data")
                .option(CoapOption.observe(0)) // register
                .build();

        var response = server.handleMessage(request, source);
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);

        // Check that observation is registered
        var observers = server.observeRegistry().getObservers("/data");
        assertThat(observers).isNotEmpty();
    }

    // ── Observe deregistration (observe=1) ──────────────────────────────────

    @Test
    void testObserveDeregistration() {
        var token = new byte[]{(byte) 0xCC};
        // First register
        var regRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(701)
                .token(token)
                .uriPath("/data")
                .option(CoapOption.observe(0))
                .build();
        server.handleMessage(regRequest, source);

        // Verify registered
        var observersBefore = server.observeRegistry().getObservers("/data");
        assertThat(observersBefore).isNotEmpty();

        // Then deregister
        var deRegRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(702)
                .token(token)
                .uriPath("/data")
                .option(CoapOption.observe(1)) // deregister
                .build();
        server.handleMessage(deRegRequest, source);

        var observersAfter = server.observeRegistry().getObservers("/data");
        assertThat(observersAfter).isEmpty();
    }

    // ── Resource management ────────────────────────────────────────────────

    @Test
    void testGetResource() {
        var resource = server.getResource("/data");
        assertThat(resource).isNotNull();
        assertThat(resource.name()).isEqualTo("data");
    }

    @Test
    void testGetNonexistentResource() {
        var resource = server.getResource("/nonexistent");
        assertThat(resource).isNull();
    }

    @Test
    void testRemoveResource() {
        server.remove("/data");
        var resource = server.getResource("/data");
        assertThat(resource).isNull();
    }

    @Test
    void testRequestRemovedResourceReturnsNotFound() {
        server.remove("/data");
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(801)
                .uriPath("/data")
                .build();

        var response = server.handleMessage(request, source);
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.NOT_FOUND);
    }

    // ── Server lifecycle ───────────────────────────────────────────────────

    @Test
    void testServerStartStop() throws IOException {
        server.stop(); // Make sure stopped first
        assertThat(server.isRunning()).isFalse();

        server.start();
        assertThat(server.isRunning()).isTrue();
        int port = server.getPort();
        assertThat(port).isGreaterThan(0);

        server.stop();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testStartTwiceThrowsIllegalStateException() throws IOException {
        server.start();
        assertThatThrownBy(() -> server.start())
                .isInstanceOf(IllegalStateException.class);
        server.stop();
    }

    @Test
    void testStopWhenNotRunningIsNoop() {
        // Server not started, stop should be a no-op (no exception)
        server.stop();
        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void testCloseDelegatesToStop() {
        server.close();
        assertThat(server.isRunning()).isFalse();
    }

    // ── Message ID generation ──────────────────────────────────────────────

    @Test
    void testNextMessageIdIncrements() {
        int id1 = server.nextMessageId();
        int id2 = server.nextMessageId();
        assertThat(id2).isGreaterThan(id1);
    }

    // ── CoapServerConfig defaults ─────────────────────────────────────────

    @Test
    void testDefaultConfig() {
        var config = CoapServerConfig.defaults();
        assertThat(config.port()).isEqualTo(5683);
        assertThat(config.maxMessageSize()).isEqualTo(1152);
        assertThat(config.maxRetransmit()).isEqualTo(4);
        assertThat(config.ackTimeout()).isEqualTo(2000L);
    }

    @Test
    void testWithPortConfig() {
        var config = CoapServerConfig.withPort(9999);
        assertThat(config.port()).isEqualTo(9999);
    }

    // ── Pending separate response count ────────────────────────────────────

    @Test
    void testPendingSeparateResponseCountInitiallyZero() {
        assertThat(server.pendingSeparateResponseCount()).isZero();
    }

    // ── Flush separate responses ──────────────────────────────────────────

    @Test
    void testFlushSeparateResponses() {
        server.flushSeparateResponses();
        // Should not throw when empty
        assertThat(server.pendingSeparateResponseCount()).isZero();
    }

    // ── Multicast constants and static helpers ────────────────────────────

    @Test
    void testMulticastConstant() {
        assertThat(CoapServer.COAP_MULTICAST_IPV4).isEqualTo("224.0.1.187");
    }

    @Test
    void testIsMulticastEnabledInitiallyFalse() {
        assertThat(server.isMulticastEnabled()).isFalse();
    }

    // ── Constructor null safety ────────────────────────────────────────────

    @Test
    void testConstructorRejectsNullConfig() {
        assertThatThrownBy(() -> new CoapServer((CoapServerConfig) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Add resource null safety ──────────────────────────────────────────

    @Test
    void testAddRejectsNullResource() {
        assertThatThrownBy(() -> server.add(null))
                .isInstanceOf(NullPointerException.class);
    }
}
