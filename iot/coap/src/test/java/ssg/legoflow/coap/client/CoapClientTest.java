package ssg.legoflow.coap.client;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.ContentFormat;
import ssg.legoflow.coap.resource.CoapExchange;
import ssg.legoflow.coap.resource.CoapResource;
import ssg.legoflow.coap.server.CoapServer;
import ssg.legoflow.coap.server.CoapServerConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapClient}.
 *
 * @since 0.1.0
 */
class CoapClientTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private CoapServer server;
    private CoapClient client;
    private final AtomicReference<String> resourceValue = new AtomicReference<>("22.5");

    @BeforeEach
    void setUp() throws IOException {
        server = new CoapServer(CoapServerConfig.withPort(PORT));
        server.add(new CoapResource("temp", "/sensors/temp") {
            @Override
            public void handleGet(CoapExchange exchange) {
                exchange.respond(CoapCode.CONTENT,
                        resourceValue.get().getBytes(StandardCharsets.UTF_8),
                        ContentFormat.TEXT_PLAIN.value());
            }

            @Override
            public void handlePost(CoapExchange exchange) {
                exchange.respond(CoapCode.CREATED,
                        "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8),
                        ContentFormat.APPLICATION_JSON.value());
            }

            @Override
            public void handlePut(CoapExchange exchange) {
                resourceValue.set(new String(exchange.getRequest().payload(), StandardCharsets.UTF_8));
                exchange.respond(CoapCode.CHANGED);
            }

            @Override
            public void handleDelete(CoapExchange exchange) {
                resourceValue.set("");
                exchange.respond(CoapCode.DELETED);
            }
        });
        server.start();
        client = new CoapClient("localhost", server.getPort());
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.stop();
    }

    @Test
    void testGet() throws IOException {
        var response = client.get("/sensors/temp");

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).isEqualTo("22.5");
    }

    @Test
    void testPost() throws IOException {
        var response = client.post("/sensors/temp",
                "new data".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.code()).isEqualTo(CoapCode.CREATED);
    }

    @Test
    void testPut() throws IOException {
        var response = client.put("/sensors/temp",
                "25.0".getBytes(StandardCharsets.UTF_8),
                ContentFormat.TEXT_PLAIN.value());

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);
        assertThat(resourceValue.get()).isEqualTo("25.0");
    }

    @Test
    void testDelete() throws IOException {
        var response = client.delete("/sensors/temp");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.DELETED);
    }

    @Test
    void testGetNotFound() throws IOException {
        var response = client.get("/nonexistent");

        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.NOT_FOUND);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    void testGetAsync() throws Exception {
        // Test that the async method returns a valid future
        var future = client.getAsync("/sensors/temp");

        assertThat(future).isNotNull();
        assertThat(future).isNotCancelled();

        var response = future.get(10, TimeUnit.SECONDS);
        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getPayloadString()).isEqualTo("22.5");
    }

    @Test
    void testNonConfirmable() throws IOException {
        client.setConfirmable(false);
        var response = client.get("/sensors/temp");

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    void testResponseContentFormat() throws IOException {
        var response = client.get("/sensors/temp");

        assertThat(response.getContentFormat()).isEqualTo(ContentFormat.TEXT_PLAIN.value());
    }

    @Test
    void testObserve() throws Exception {
        var received = new CompletableFuture<CoapResponse>();
        var relation = client.observe("/sensors/temp", received::complete);

        assertThat(relation).isNotNull();
        assertThat(relation.isActive()).isTrue();

        // Clean up
        client.cancelObserve(relation);
        assertThat(relation.isActive()).isFalse();
    }

    @Test
    void testClientClose() throws IOException {
        var tempClient = new CoapClient("localhost", server.getPort());
        tempClient.close();
        // Should not throw or hang
    }
}
