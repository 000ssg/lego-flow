package ssg.legoflow.coap.block;

import java.util.Objects;

/**
 * Represents a CoAP Block option value as defined in RFC 7959.
 *
 * <p>A Block option encodes three fields:
 * <ul>
 *   <li>{@code num} — the block number (variable size, 4-20 bits)</li>
 *   <li>{@code more} — whether more blocks follow (1 bit)</li>
 *   <li>{@code szx} — the size exponent (3 bits), where blockSize = 2^(szx + 4)</li>
 * </ul>
 *
 * <p>The option value is encoded as 1-3 bytes:
 * <pre>
 *   value = (num << 4) | (more ? 0x08 : 0) | szx
 * </pre>
 *
 * @param num  the block number (0-based)
 * @param more whether more blocks follow
 * @param szx  the size exponent (0-6), where blockSize = 2^(szx + 4)
 * @since 0.1.0
 */
public record BlockOption(int num, boolean more, int szx) {

    /**
     * Compact constructor with validation.
     *
     * @param num  the block number; must be non-negative
     * @param more whether more blocks follow
     * @param szx  the size exponent; must be 0-6
     * @throws IllegalArgumentException if {@code num} is negative or {@code szx} is out of range
     */
    public BlockOption {
        if (num < 0) {
            throw new IllegalArgumentException("Block number must be non-negative: " + num);
        }
        if (szx < 0 || szx > 6) {
            throw new IllegalArgumentException("Size exponent must be 0-6: " + szx);
        }
    }

    /**
     * Returns the block size in bytes, calculated as 2^(szx + 4).
     *
     * <p>Valid sizes are: 16, 32, 64, 128, 256, 512, 1024.
     *
     * @return the block size in bytes
     * @since 0.1.0
     */
    public int getBlockSize() {
        return 1 << (szx + 4);
    }

    /**
     * Encodes this block option to a byte array suitable for use as an option value.
     *
     * @return the encoded bytes (1-3 bytes)
     * @since 0.1.0
     */
    public byte[] encode() {
        int value = (num << 4) | (more ? 0x08 : 0) | szx;
        if (value == 0) {
            return new byte[]{0};
        }
        if (value <= 0xFF) {
            return new byte[]{(byte) value};
        }
        if (value <= 0xFFFF) {
            return new byte[]{
                    (byte) ((value >> 8) & 0xFF),
                    (byte) (value & 0xFF)
            };
        }
        return new byte[]{
                (byte) ((value >> 16) & 0xFF),
                (byte) ((value >> 8) & 0xFF),
                (byte) (value & 0xFF)
        };
    }

    /**
     * Decodes a block option from the given byte array.
     *
     * @param data the encoded bytes (1-3 bytes)
     * @return the decoded block option
     * @throws IllegalArgumentException if the data length is invalid
     * @since 0.1.0
     */
    public static BlockOption decode(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (data.length == 0 || data.length > 3) {
            throw new IllegalArgumentException("Block option data must be 1-3 bytes: " + data.length);
        }

        int value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
        }

        int szx = value & 0x07;
        boolean more = (value & 0x08) != 0;
        int num = value >> 4;

        return new BlockOption(num, more, szx);
    }

    /**
     * Returns the size exponent for the given block size.
     *
     * @param blockSize the block size in bytes (must be a power of 2, 16-1024)
     * @return the size exponent
     * @throws IllegalArgumentException if the block size is invalid
     * @since 0.1.0
     */
    public static int szxFromBlockSize(int blockSize) {
        return switch (blockSize) {
            case 16 -> 0;
            case 32 -> 1;
            case 64 -> 2;
            case 128 -> 3;
            case 256 -> 4;
            case 512 -> 5;
            case 1024 -> 6;
            default -> throw new IllegalArgumentException("Invalid block size: " + blockSize);
        };
    }
}
