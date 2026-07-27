package ssg.legoflow.coap.block;

import ssg.legoflow.coap.protocol.CoapCode;
import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;
import ssg.legoflow.coap.protocol.CoapType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link BlockTransfer}.
 *
 * @since 1.0.0
 */
class BlockTransferTest {

    @Test
    void testSplitPayloadExactFit() {
        var payload = new byte[256]; // Exactly 4 blocks of 64 bytes
        var blocks = BlockTransfer.splitPayload(payload, 64);

        assertThat(blocks).hasSize(4);
        assertThat(blocks.get(0).num()).isZero();
        assertThat(blocks.get(0).more()).isTrue();
        assertThat(blocks.get(3).num()).isEqualTo(3);
        assertThat(blocks.get(3).more()).isFalse();
    }

    @Test
    void testSplitPayloadWithRemainder() {
        var payload = new byte[100]; // 2 full blocks of 64 + partial
        var blocks = BlockTransfer.splitPayload(payload, 64);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).more()).isTrue();
        assertThat(blocks.get(1).more()).isFalse();
    }

    @Test
    void testGetBlockPayload() {
        var payload = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        var block0 = new BlockOption(0, true, 2); // 64-byte blocks
        var block0Payload = BlockTransfer.getBlockPayload(payload, block0);

        assertThat(block0Payload).isEqualTo(payload); // All fits in one block
    }

    @Test
    void testAssembleBlocks() {
        var messages = new ArrayList<CoapMessage>();
        messages.add(CoapMessage.builder()
                .code(CoapCode.CONTENT)
                .type(CoapType.ACKNOWLEDGEMENT)
                .option(new CoapOption(CoapOption.BLOCK2, new BlockOption(0, true, 2).encode()))
                .payload("Hello, ")
                .build());
        messages.add(CoapMessage.builder()
                .code(CoapCode.CONTENT)
                .type(CoapType.ACKNOWLEDGEMENT)
                .option(new CoapOption(CoapOption.BLOCK2, new BlockOption(1, false, 2).encode()))
                .payload("World!")
                .build());

        var assembled = BlockTransfer.assembleBlocks(messages);
        assertThat(new String(assembled, StandardCharsets.UTF_8)).isEqualTo("Hello, World!");
    }

    @Test
    void testSplitPayloadSmallBlockSize() {
        var payload = new byte[128]; // 8 blocks of 16 bytes
        var blocks = BlockTransfer.splitPayload(payload, 16);

        assertThat(blocks).hasSize(8);
        for (int i = 0; i < 7; i++) {
            assertThat(blocks.get(i).more()).isTrue();
        }
        assertThat(blocks.get(7).more()).isFalse();
    }

    @Test
    void testSplitEmptyPayload() {
        var payload = new byte[0];
        var blocks = BlockTransfer.splitPayload(payload, 64);

        assertThat(blocks).hasSize(1);
        assertThat(blocks.getFirst().num()).isZero();
        assertThat(blocks.getFirst().more()).isFalse();
    }
}
