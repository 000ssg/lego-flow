package ssg.legoflow.network.common.ber;

import ssg.legoflow.network.common.asn1.Asn1Tag;

import java.nio.ByteBuffer;

/**
 * BER tag encoding and decoding.
 *
 * <p>Handles both short-form (tag number 0-30, single byte) and long-form
 * (tag number 31+, multi-byte with continuation bits) tag encoding.
 *
 * <p>Tag byte layout:
 * <pre>
 *   Bits 7-6: class (00=UNIVERSAL, 01=APPLICATION, 10=CONTEXT, 11=PRIVATE)
 *   Bit 5:    constructed (1) or primitive (0)
 *   Bits 4-0: tag number (0-30), or 11111 (31) for long form
 * </pre>
 *
 * @since 1.0.0
 */
public final class BerTag {

    private BerTag() {}

    /**
     * Encodes an ASN.1 tag into the given buffer.
     *
     * @param tag    the tag to encode
     * @param buffer the output buffer
     */
    public static void encode(Asn1Tag tag, ByteBuffer buffer) {
        int firstByte = (tag.tagClass().value() << 6);
        if (tag.constructed()) {
            firstByte |= 0x20;
        }

        if (tag.number() < 31) {
            firstByte |= tag.number();
            buffer.put((byte) firstByte);
        } else {
            firstByte |= 0x1F;
            buffer.put((byte) firstByte);
            encodeTagNumber(tag.number(), buffer);
        }
    }

    /**
     * Decodes a tag from the given buffer.
     *
     * @param buffer the input buffer
     * @return the decoded tag
     * @throws BerDecodingException if the buffer is exhausted or the tag is malformed
     */
    public static Asn1Tag decode(ByteBuffer buffer) {
        if (!buffer.hasRemaining()) {
            throw new BerDecodingException("Buffer exhausted while reading tag");
        }
        int firstByte = buffer.get() & 0xFF;
        Asn1Tag.TagClass tagClass = Asn1Tag.TagClass.of((firstByte >> 6) & 0x03);
        boolean constructed = (firstByte & 0x20) != 0;
        int number = firstByte & 0x1F;

        if (number == 0x1F) {
            number = decodeTagNumber(buffer);
        }

        return new Asn1Tag(tagClass, constructed, number);
    }

    /**
     * Returns the number of bytes needed to encode the given tag.
     *
     * @param tag the tag
     * @return the encoded length in bytes
     */
    public static int encodedLength(Asn1Tag tag) {
        if (tag.number() < 31) {
            return 1;
        }
        int len = 1; // first byte
        int number = tag.number();
        // count base-128 bytes
        int n = 0;
        do {
            n++;
            number >>= 7;
        } while (number > 0);
        return len + n;
    }

    private static void encodeTagNumber(int number, ByteBuffer buffer) {
        // Determine how many base-128 bytes are needed
        int temp = number;
        int byteCount = 0;
        do {
            byteCount++;
            temp >>= 7;
        } while (temp > 0);

        // Encode in big-endian order with continuation bits
        for (int i = byteCount - 1; i >= 0; i--) {
            int b = (number >> (7 * i)) & 0x7F;
            if (i > 0) {
                b |= 0x80; // continuation bit
            }
            buffer.put((byte) b);
        }
    }

    private static int decodeTagNumber(ByteBuffer buffer) {
        int number = 0;
        int b;
        do {
            if (!buffer.hasRemaining()) {
                throw new BerDecodingException("Buffer exhausted while reading long-form tag number");
            }
            b = buffer.get() & 0xFF;
            number = (number << 7) | (b & 0x7F);
        } while ((b & 0x80) != 0);
        return number;
    }
}
