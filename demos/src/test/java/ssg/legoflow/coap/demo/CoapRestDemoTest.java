package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for {@link CoapRestDemo}.
 *
 * @since 0.1.0
 */
class CoapRestDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private CoapRestDemo demo;
    private final InetSocketAddress source = new InetSocketAddress("localhost", 12345);

    @BeforeEach
    void setUp() throws IOException {
        demo = new CoapRestDemo(PORT);
        demo.start();
    }

    @AfterEach
    void tearDown() {
        if (demo != null) demo.stop();
    }

    @Test
    void testListItemsEmpty() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/items")
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).isEqualTo("[]");
    }

    @Test
    void testCreateItem() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(2)
                .uriPath("/items")
                .payload("test item".getBytes(StandardCharsets.UTF_8))
                .build();

        var response = demo.server().handleMessage(request, source);

        assertThat(response.code()).isEqualTo(CoapCode.CREATED);
        assertThat(demo.collectionResource().items()).hasSize(1);
    }

    @Test
    void testCreateAndGetItem() {
        // Create
        var createRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(3)
                .uriPath("/items")
                .payload("test data".getBytes(StandardCharsets.UTF_8))
                .build();
        demo.server().handleMessage(createRequest, source);

        // Get the item
        var getRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(4)
                .uriPath("/items/1")
                .build();

        var response = demo.server().handleMessage(getRequest, source);

        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.getPayloadString()).contains("test data");
    }

    @Test
    void testUpdateItem() {
        // Create first
        var createRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(5)
                .uriPath("/items")
                .payload("original".getBytes(StandardCharsets.UTF_8))
                .build();
        demo.server().handleMessage(createRequest, source);

        // Update
        var updateRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.PUT)
                .messageId(6)
                .uriPath("/items/1")
                .payload("updated".getBytes(StandardCharsets.UTF_8))
                .build();

        var response = demo.server().handleMessage(updateRequest, source);

        assertThat(response.code()).isEqualTo(CoapCode.CHANGED);
        assertThat(demo.collectionResource().items().get("1")).isEqualTo("updated");
    }

    @Test
    void testDeleteItem() {
        // Create first
        var createRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.POST)
                .messageId(7)
                .uriPath("/items")
                .payload("to delete".getBytes(StandardCharsets.UTF_8))
                .build();
        demo.server().handleMessage(createRequest, source);

        // Delete
        var deleteRequest = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.DELETE)
                .messageId(8)
                .uriPath("/items/1")
                .build();

        var response = demo.server().handleMessage(deleteRequest, source);

        assertThat(response.code()).isEqualTo(CoapCode.DELETED);
        assertThat(demo.collectionResource().items()).isEmpty();
    }
}
