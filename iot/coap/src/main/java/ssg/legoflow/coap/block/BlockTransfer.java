package ssg.legoflow.coap.block;

import ssg.legoflow.coap.protocol.CoapMessage;
import ssg.legoflow.coap.protocol.CoapOption;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Manages blockwise transfers as defined in RFC 7959.
 *
 * <p>Supports both Block1 (client-to-server request payload) and Block2
 * (server-to-client response payload) transfers by splitting large payloads
 * into blocks and reassembling them.
 *
 * @since 0.1.0
 */
public final class BlockTransfer {

    private BlockTransfer() {
        // Utility class
    }

    /**
     * Splits a payload into a list of block options describing each block.
     *
     * @param payload   the full payload to split
     * @param blockSize the desired block size in bytes (16, 32, 64, 128, 256, 512, or 1024)
     * @return a list of {@link BlockOption} instances describing each block
     * @throws NullPointerException     if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code blockSize} is invalid
     * @since 0.1.0
     */
    public static List<BlockOption> splitPayload(byte[] payload, int blockSize) {
        Objects.requireNonNull(payload, "payload must not be null");
        int szx = BlockOption.szxFromBlockSize(blockSize);

        var blocks = new ArrayList<BlockOption>();
        int totalBlocks = (payload.length + blockSize - 1) / blockSize;

        if (totalBlocks == 0) {
            blocks.add(new BlockOption(0, false, szx));
            return blocks;
        }

        for (int i = 0; i < totalBlocks; i++) {
            boolean more = (i < totalBlocks - 1);
            blocks.add(new BlockOption(i, more, szx));
        }

        return blocks;
    }

    /**
     * Extracts the payload bytes for a specific block from the full payload.
     *
     * @param payload   the full payload
     * @param block     the block option identifying which block to extract
     * @return the block's payload bytes
     * @since 0.1.0
     */
    public static byte[] getBlockPayload(byte[] payload, BlockOption block) {
        Objects.requireNonNull(payload, "payload must not be null");
        Objects.requireNonNull(block, "block must not be null");

        int blockSize = block.getBlockSize();
        int offset = block.num() * blockSize;

        if (offset >= payload.length) {
            return new byte[0];
        }

        int length = Math.min(blockSize, payload.length - offset);
        var result = new byte[length];
        System.arraycopy(payload, offset, result, 0, length);
        return result;
    }

    /**
     * Assembles a complete payload from a list of block-carrying messages.
     *
     * <p>Messages are sorted by their Block1 or Block2 option's block number
     * before assembly. The method checks Block2 first, then Block1.
     *
     * @param messages the list of messages carrying block payloads
     * @return the assembled complete payload
     * @throws IllegalArgumentException if messages are empty or missing block options
     * @since 0.1.0
     */
    public static byte[] assembleBlocks(List<CoapMessage> messages) {
        Objects.requireNonNull(messages, "messages must not be null");
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Cannot assemble from empty message list");
        }

        // Determine which block option to use (Block2 or Block1)
        int blockOptionNumber = CoapOption.BLOCK2;
        if (messages.getFirst().getOption(CoapOption.BLOCK2) == null) {
            blockOptionNumber = CoapOption.BLOCK1;
        }

        final int optNum = blockOptionNumber;
        var sorted = new ArrayList<>(messages);
        sorted.sort(Comparator.comparingInt(m -> {
            var opt = m.getOption(optNum);
            if (opt == null) return 0;
            return BlockOption.decode(opt.value()).num();
        }));

        var output = new ByteArrayOutputStream();
        for (var message : sorted) {
            byte[] payload = message.payload();
            if (payload.length > 0) {
                output.writeBytes(payload);
            }
        }

        return output.toByteArray();
    }

    /**
     * Returns whether a message contains a Block1 option.
     *
     * @param message the message to check
     * @return {@code true} if the message has a Block1 option
     * @since 0.1.0
     */
    public static boolean hasBlock1(CoapMessage message) {
        return message.getOption(CoapOption.BLOCK1) != null;
    }

    /**
     * Returns whether a message contains a Block2 option.
     *
     * @param message the message to check
     * @return {@code true} if the message has a Block2 option
     * @since 0.1.0
     */
    public static boolean hasBlock2(CoapMessage message) {
        return message.getOption(CoapOption.BLOCK2) != null;
    }

    /**
     * Extracts the Block1 option from a message.
     *
     * @param message the message
     * @return the Block1 option, or {@code null} if absent
     * @since 0.1.0
     */
    public static BlockOption getBlock1(CoapMessage message) {
        var opt = message.getOption(CoapOption.BLOCK1);
        return opt != null ? BlockOption.decode(opt.value()) : null;
    }

    /**
     * Extracts the Block2 option from a message.
     *
     * @param message the message
     * @return the Block2 option, or {@code null} if absent
     * @since 0.1.0
     */
    public static BlockOption getBlock2(CoapMessage message) {
        var opt = message.getOption(CoapOption.BLOCK2);
        return opt != null ? BlockOption.decode(opt.value()) : null;
    }
}
