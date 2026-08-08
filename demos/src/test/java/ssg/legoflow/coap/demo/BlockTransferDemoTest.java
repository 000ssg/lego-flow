package ssg.legoflow.coap.demo;

import ssg.legoflow.coap.block.BlockOption;
import ssg.legoflow.coap.block.BlockTransfer;
import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BlockTransferDemo}.
 *
 * @since 0.1.0
 */
class BlockTransferDemoTest {

    /** Use ephemeral port (0) to avoid port conflicts under parallel test execution. */
    private static final int PORT = 0;
    private BlockTransferDemo demo;

    @BeforeEach
    void setUp() throws IOException {
        demo = new BlockTransferDemo(PORT);
        demo.start();
    }

    @AfterEach
    void tearDown() {
        if (demo != null) demo.stop();
    }

    @Test
    void testLargeResourceExists() {
        assertThat(demo.largeResource()).isNotNull();
        assertThat(demo.largeResource().currentData()).isNotEmpty();
    }

    @Test
    void testGenerateLargePayload() {
        var payload = BlockTransferDemo.generateLargePayload(1000);

        assertThat(payload).hasSize(1000);
    }

    @Test
    void testSplitAndAssemble() {
        var payload = BlockTransferDemo.generateLargePayload(2048);
        var blocks = BlockTransfer.splitPayload(payload, 256);

        assertThat(blocks).hasSize(8);

        // Verify each block's payload and reassemble
        var messages = new java.util.ArrayList<CoapMessage>();
        for (var block : blocks) {
            var blockPayload = BlockTransfer.getBlockPayload(payload, block);
            messages.add(CoapMessage.builder()
                    .code(CoapCode.CONTENT)
                    .type(CoapType.ACKNOWLEDGEMENT)
                    .option(new ssg.legoflow.coap.protocol.CoapOption(
                            ssg.legoflow.coap.protocol.CoapOption.BLOCK2,
                            block.encode()))
                    .payload(blockPayload)
                    .build());
        }

        var assembled = BlockTransfer.assembleBlocks(messages);
        assertThat(assembled).isEqualTo(payload);
    }

    @Test
    void testGetLargeResource() {
        var request = CoapMessage.builder()
                .type(CoapType.CONFIRMABLE)
                .code(CoapCode.GET)
                .messageId(1)
                .uriPath("/large")
                .build();

        var response = demo.server().handleMessage(request, new InetSocketAddress("localhost", 12345));
        assertThat(response).isNotNull();
        assertThat(response.code()).isEqualTo(CoapCode.CONTENT);
        assertThat(response.payload()).isNotEmpty();
    }
}
