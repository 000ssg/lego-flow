package ssg.legoflow.network.common.ber;

import ssg.legoflow.network.common.asn1.Asn1Tag;
import java.nio.ByteBuffer;
/**
 * Utility methods for BER tag and length operations.
 *
 * @since 0.1.0
 */
public final class BerUtils {

    private BerUtils() {}

    /**
     * Peeks at the next tag in the buffer without consuming it.
     *
     * @param buffer the input buffer
     * @return the next tag
     * @throws BerDecodingException if the buffer is empty
     */
    public static Asn1Tag peekTag(ByteBuffer buffer) {
        int pos = buffer.position();
        Asn1Tag tag = BerTag.decode(buffer);
        buffer.position(pos);
        return tag;
    }

    /**
     * Checks whether the buffer has an end-of-contents marker (0x00 0x00) at the current position.
     *
     * @param buffer the input buffer
     * @return true if the next two bytes are 0x00 0x00
     */
    public static boolean isEndOfContents(ByteBuffer buffer) {
        if (buffer.remaining() < 2) {
            return false;
        }
        int pos = buffer.position();
        boolean eoc = buffer.get(pos) == 0x00 && buffer.get(pos + 1) == 0x00;
        return eoc;
    }

    /**
     * Consumes the end-of-contents marker (0x00 0x00) from the buffer.
     *
     * @param buffer the input buffer
     * @throws BerDecodingException if the marker is not present
     */
    public static void consumeEndOfContents(ByteBuffer buffer) {
        if (buffer.remaining() < 2) {
            throw new BerDecodingException("Expected end-of-contents but buffer has less than 2 bytes");
        }
        byte b1 = buffer.get();
        byte b2 = buffer.get();
        if (b1 != 0x00 || b2 != 0x00) {
            throw new BerDecodingException(
                    String.format("Expected end-of-contents (00 00) but got %02X %02X", b1, b2));
        }
    }

    /**
     * Returns the total encoded TLV size for the given content length and tag.
     *
     * @param tag           the tag
     * @param contentLength the content length
     * @return the total TLV size in bytes
     */
    public static int tlvSize(Asn1Tag tag, int contentLength) {
        return BerTag.encodedLength(tag) + BerLength.encodedLength(contentLength) + contentLength;
    }

    /**
     * Encodes a base-128 variable-length integer (used in OID arcs).
     *
     * @param value  the value to encode
     * @param buffer the output buffer
     */
    public static void encodeBase128(int value, ByteBuffer buffer) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative: " + value);
        }
        if (value <= 127) {
            buffer.put((byte) value);
            return;
        }
        int byteCount = 0;
        int temp = value;
        do {
            byteCount++;
            temp >>= 7;
        } while (temp > 0);

        for (int i = byteCount - 1; i >= 0; i--) {
            int b = (value >> (7 * i)) & 0x7F;
            if (i > 0) {
                b |= 0x80;
            }
            buffer.put((byte) b);
        }
    }

    /**
     * Decodes a base-128 variable-length integer from the buffer.
     *
     * @param buffer the input buffer
     * @return the decoded value
     */
    public static int decodeBase128(ByteBuffer buffer) {
        int value = 0;
        int b;
        do {
            if (!buffer.hasRemaining()) {
                throw new BerDecodingException("Buffer exhausted while reading base-128 value");
            }
            b = buffer.get() & 0xFF;
            value = (value << 7) | (b & 0x7F);
        } while ((b & 0x80) != 0);
        return value;
    }

    /**
     * Returns the number of bytes needed to encode a base-128 value.
     *
     * @param value the value
     * @return the number of bytes
     */
    public static int base128Length(int value) {
        if (value <= 127) return 1;
        int count = 0;
        int temp = value;
        do {
            count++;
            temp >>= 7;
        } while (temp > 0);
        return count;
    }
}
