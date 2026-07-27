package ssg.legoflow.coap.codec;

import ssg.legoflow.coap.protocol.CoapOption;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Encoder and decoder for CoAP options using delta encoding as defined in RFC 7252, Section 3.1.
 *
 * <p>Options are encoded as a sequence of (option delta, option length, value) tuples.
 * Both delta and length use extended formats for values 13 and 14:
 * <ul>
 *   <li>0-12: inline value</li>
 *   <li>13: one extended byte, value = byte + 13</li>
 *   <li>14: two extended bytes, value = uint16 + 269</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class CoapOptionCodec {

    private CoapOptionCodec() {
        // Utility class
    }

    /**
     * Encodes a list of options into a byte buffer using delta encoding.
     *
     * <p>Options are sorted by option number before encoding, as required by the protocol.
     *
     * @param options the options to encode
     * @return a byte buffer containing the encoded options (flipped, ready for reading)
     * @since 1.0.0
     */
    public static ByteBuffer encode(List<CoapOption> options) {
        if (options == null || options.isEmpty()) {
            return ByteBuffer.allocate(0);
        }

        var sorted = new ArrayList<>(options);
        sorted.sort(Comparator.comparingInt(CoapOption::number));

        var buffer = ByteBuffer.allocate(estimateSize(sorted));
        int previousNumber = 0;

        for (var option : sorted) {
            int delta = option.number() - previousNumber;
            byte[] value = option.value();
            int length = value.length;

            int deltaBase = encodeBase(delta);
            int lengthBase = encodeBase(length);

            buffer.put((byte) ((deltaBase << 4) | lengthBase));
            writeExtended(buffer, delta, deltaBase);
            writeExtended(buffer, length, lengthBase);

            if (length > 0) {
                buffer.put(value);
            }

            previousNumber = option.number();
        }

        buffer.flip();
        return buffer;
    }

    /**
     * Decodes options from a byte buffer using delta encoding.
     *
     * @param buffer the buffer containing encoded options (position at start of options)
     * @return the decoded list of options
     * @since 1.0.0
     */
    public static List<CoapOption> decode(ByteBuffer buffer) {
        var options = new ArrayList<CoapOption>();
        int previousNumber = 0;

        while (buffer.hasRemaining()) {
            int firstByte = buffer.get() & 0xFF;

            // Check for payload marker
            if (firstByte == 0xFF) {
                // Put it back so the caller can handle the payload marker
                buffer.position(buffer.position() - 1);
                break;
            }

            int deltaBase = (firstByte >> 4) & 0x0F;
            int lengthBase = firstByte & 0x0F;

            if (deltaBase == 15 || lengthBase == 15) {
                throw new IllegalArgumentException("Invalid option delta/length: reserved value 15");
            }

            int delta = readExtended(buffer, deltaBase);
            int length = readExtended(buffer, lengthBase);
            int optionNumber = previousNumber + delta;

            var value = new byte[length];
            if (length > 0) {
                buffer.get(value);
            }

            options.add(new CoapOption(optionNumber, value));
            previousNumber = optionNumber;
        }

        return options;
    }

    private static int encodeBase(int value) {
        if (value < 13) {
            return value;
        } else if (value < 269) {
            return 13;
        } else {
            return 14;
        }
    }

    private static void writeExtended(ByteBuffer buffer, int value, int base) {
        if (base == 13) {
            buffer.put((byte) (value - 13));
        } else if (base == 14) {
            int extended = value - 269;
            buffer.putShort((short) extended);
        }
    }

    private static int readExtended(ByteBuffer buffer, int base) {
        if (base < 13) {
            return base;
        } else if (base == 13) {
            return (buffer.get() & 0xFF) + 13;
        } else { // base == 14
            return (buffer.getShort() & 0xFFFF) + 269;
        }
    }

    private static int estimateSize(List<CoapOption> options) {
        int size = 0;
        int previousNumber = 0;
        for (var option : options) {
            int delta = option.number() - previousNumber;
            int length = option.value().length;

            size += 1; // base byte
            size += extendedSize(delta);
            size += extendedSize(length);
            size += length;

            previousNumber = option.number();
        }
        return size;
    }

    private static int extendedSize(int value) {
        if (value < 13) return 0;
        if (value < 269) return 1;
        return 2;
    }
}
