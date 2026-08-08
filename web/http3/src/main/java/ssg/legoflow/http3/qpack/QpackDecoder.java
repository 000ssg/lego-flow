package ssg.legoflow.http3.qpack;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QPACK header decoder as defined in RFC 9204.
 *
 * <p>Decodes QPACK wire format header blocks into lists of header field
 * name-value pairs. Handles indexed field lines (static and dynamic),
 * literal field lines with name references, and literal field lines
 * with literal names.</p>
 *
 * <p>Supports encoder stream instructions for dynamic table updates:
 * Insert With Name Reference (static/dynamic), Insert With Literal Name,
 * Set Dynamic Table Capacity, and Duplicate.</p>
 *
 * <p>Generates decoder instructions (Section Acknowledgment, Stream Cancellation,
 * Insert Count Increment) that should be sent on the QPACK decoder stream.</p>
 *
 * @since 0.1.0
 */
public class QpackDecoder {

    private final QpackDynamicTable dynamicTable;
    private final ByteArrayOutputStream decoderInstructions = new ByteArrayOutputStream();

    /**
     * Creates a new decoder with default dynamic table capacity (4096 bytes).
     *
     * @since 0.1.0
     */
    public QpackDecoder() {
        this(4096);
    }

    /**
     * Creates a new decoder with the given dynamic table capacity.
     *
     * @param maxTableCapacity the maximum dynamic table capacity in bytes
     * @since 0.1.0
     */
    public QpackDecoder(int maxTableCapacity) {
        this.dynamicTable = new QpackDynamicTable(maxTableCapacity);
    }

    /**
     * Decodes a QPACK-encoded header block into header field entries.
     *
     * <p>Parses the header block prefix (Required Insert Count and Delta Base),
     * then decodes each header field representation. Dynamic table references
     * are resolved using the base computed from the prefix.</p>
     *
     * @param data the encoded header block
     * @return a list of name-value pairs
     * @since 0.1.0
     */
    public List<Map.Entry<String, String>> decode(ByteBuffer data) {
        var headers = new ArrayList<Map.Entry<String, String>>();
        var buf = data.duplicate();

        // Read header block prefix
        int encodedRequiredInsertCount = decodeInteger(buf, 8);
        int requiredInsertCount;
        if (encodedRequiredInsertCount == 0) {
            requiredInsertCount = 0;
        } else {
            try {
                requiredInsertCount = dynamicTable.decodeRequiredInsertCount(encodedRequiredInsertCount);
            } catch (IllegalArgumentException e) {
                // Fall back — treat as the encoded value directly for backwards compatibility
                requiredInsertCount = encodedRequiredInsertCount;
            }
        }

        // Read Delta Base (sign bit in MSB of first octet)
        int deltaBaseByte = buf.get(buf.position()) & 0xFF;
        boolean signBit = (deltaBaseByte & 0x80) != 0;
        int deltaBase = decodeInteger(buf, 7);
        int base;
        if (requiredInsertCount == 0) {
            base = 0;
        } else if (signBit) {
            base = requiredInsertCount - deltaBase - 1;
        } else {
            base = requiredInsertCount + deltaBase;
        }

        while (buf.hasRemaining()) {
            int b = buf.get(buf.position()) & 0xFF;

            if ((b & 0xC0) == 0xC0) {
                // Indexed field line, static reference: 11xxxxxx
                int index = decodeInteger(buf, 6);
                var entry = QpackStaticTable.getEntry(index);
                headers.add(new AbstractMap.SimpleEntry<>(entry.name(), entry.value()));
            } else if ((b & 0xC0) == 0x80) {
                // Indexed field line, dynamic reference: 10xxxxxx
                int index = decodeInteger(buf, 6);
                var entry = dynamicTable.getEntry(index);
                headers.add(new AbstractMap.SimpleEntry<>(entry.name(), entry.value()));
            } else if ((b & 0xF0) == 0x10) {
                // Indexed field line with post-base index: 0001xxxx
                int postBaseIndex = decodeInteger(buf, 4);
                var entry = dynamicTable.getEntryPostBase(postBaseIndex, base);
                headers.add(new AbstractMap.SimpleEntry<>(entry.name(), entry.value()));
            } else if ((b & 0xE0) == 0x40) {
                // Literal with name reference: 01NTxxxx
                boolean isStatic = (b & 0x10) != 0;
                int index = decodeInteger(buf, 4);
                String name;
                if (isStatic) {
                    name = QpackStaticTable.getEntry(index).name();
                } else {
                    name = dynamicTable.getEntry(index).name();
                }
                String value = decodeString(buf);
                headers.add(new AbstractMap.SimpleEntry<>(name, value));
            } else if ((b & 0xE0) == 0x20) {
                // Literal with literal name: 001Nxxxx
                buf.get(); // consume the prefix byte
                String name = decodeString(buf);
                String value = decodeString(buf);
                headers.add(new AbstractMap.SimpleEntry<>(name, value));
            } else if ((b & 0xF8) == 0x00) {
                // Literal with post-base name reference: 0000Nxxx
                int index = decodeInteger(buf, 3);
                var entry = dynamicTable.getEntryPostBase(index, base);
                String value = decodeString(buf);
                headers.add(new AbstractMap.SimpleEntry<>(entry.name(), value));
            } else {
                // Unknown pattern — skip
                buf.get();
            }
        }

        return headers;
    }

    /**
     * Processes encoder stream instructions, updating the dynamic table.
     *
     * <p>Encoder instructions include: Set Dynamic Table Capacity,
     * Insert With Name Reference (static/dynamic), Insert With Literal Name,
     * and Duplicate.</p>
     *
     * @param data the encoder instruction data
     * @since 0.1.0
     */
    public void processEncoderInstructions(ByteBuffer data) {
        var buf = data.duplicate();
        while (buf.hasRemaining()) {
            int b = buf.get(buf.position()) & 0xFF;

            if ((b & 0xE0) == 0x20) {
                // Set Dynamic Table Capacity: 001xxxxx
                int capacity = decodeInteger(buf, 5);
                dynamicTable.setCapacity(capacity);
            } else if ((b & 0xC0) == 0xC0) {
                // Insert With Name Reference (static): 11xxxxxx
                int staticIndex = decodeInteger(buf, 6);
                String value = decodeString(buf);
                dynamicTable.insertWithStaticNameReference(staticIndex, value);
            } else if ((b & 0xC0) == 0x80) {
                // Insert With Name Reference (dynamic): 10xxxxxx
                int relativeIndex = decodeInteger(buf, 6);
                String value = decodeString(buf);
                dynamicTable.insertWithDynamicNameReference(relativeIndex, value);
            } else if ((b & 0xE0) == 0x40) {
                // Insert With Literal Name: 01xxxxxx
                buf.get(); // consume prefix byte
                String name = decodeString(buf);
                String value = decodeString(buf);
                dynamicTable.insert(name, value);
            } else if ((b & 0xE0) == 0x00) {
                // Duplicate: 000xxxxx
                int relativeIndex = decodeInteger(buf, 5);
                dynamicTable.duplicate(relativeIndex);
            } else {
                buf.get(); // skip unknown
            }
        }
    }

    /**
     * Generates a Section Acknowledgment decoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.4.1) tells the encoder that the
     * decoder has successfully processed a header block on the given stream.</p>
     *
     * @param streamId the QUIC stream ID
     * @return a {@link ByteBuffer} containing the decoder instruction
     * @since 0.1.0
     */
    public ByteBuffer encodeSectionAcknowledgment(long streamId) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 1xxxxxxx
        QpackEncoder.encodeInteger(out, (int) streamId, 7, 0x80);
        dynamicTable.acknowledgeSectionForStream(streamId);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates a Stream Cancellation decoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.4.2) tells the encoder that the
     * indicated stream was cancelled and references to that stream's header
     * block can be released.</p>
     *
     * @param streamId the QUIC stream ID
     * @return a {@link ByteBuffer} containing the decoder instruction
     * @since 0.1.0
     */
    public ByteBuffer encodeStreamCancellation(long streamId) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 01xxxxxx
        QpackEncoder.encodeInteger(out, (int) streamId, 6, 0x40);
        dynamicTable.cancelStream(streamId);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates an Insert Count Increment decoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.4.3) tells the encoder that the
     * decoder has received and can reference additional dynamic table entries.</p>
     *
     * @param increment the number of new entries received
     * @return a {@link ByteBuffer} containing the decoder instruction
     * @since 0.1.0
     */
    public ByteBuffer encodeInsertCountIncrement(int increment) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 00xxxxxx
        QpackEncoder.encodeInteger(out, increment, 6, 0x00);
        dynamicTable.incrementKnownReceivedCount(increment);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Drains accumulated decoder instructions that should be sent on the
     * QPACK decoder stream.
     *
     * @return a {@link ByteBuffer} containing the decoder instructions, or empty if none
     * @since 0.1.0
     */
    public ByteBuffer drainDecoderInstructions() {
        if (decoderInstructions.size() == 0) {
            return ByteBuffer.allocate(0);
        }
        var data = ByteBuffer.wrap(decoderInstructions.toByteArray());
        decoderInstructions.reset();
        return data;
    }

    /**
     * Decodes an integer from the buffer using QPACK/HPACK integer encoding.
     *
     * @param buf        the source buffer
     * @param prefixBits the number of prefix bits
     * @return the decoded integer value
     * @since 0.1.0
     */
    public static int decodeInteger(ByteBuffer buf, int prefixBits) {
        int maxPrefix = (1 << prefixBits) - 1;
        int b = buf.get() & 0xFF;
        int value = b & maxPrefix;

        if (value < maxPrefix) {
            return value;
        }

        int shift = 0;
        do {
            b = buf.get() & 0xFF;
            value += (b & 0x7F) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);

        return value;
    }

    /**
     * Decodes a string from the buffer, handling Huffman encoding.
     *
     * @param buf the source buffer
     * @return the decoded string
     * @since 0.1.0
     */
    public static String decodeString(ByteBuffer buf) {
        int b = buf.get(buf.position()) & 0xFF;
        boolean huffmanEncoded = (b & 0x80) != 0;
        int length = decodeInteger(buf, 7);

        byte[] data = new byte[length];
        buf.get(data);

        if (huffmanEncoded) {
            return QpackHuffman.decode(data);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Returns the dynamic table used by this decoder.
     *
     * @return the dynamic table
     * @since 0.1.0
     */
    public QpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    /**
     * Sets the maximum dynamic table capacity.
     *
     * @param maxCapacity the new maximum capacity
     * @since 0.1.0
     */
    public void setMaxTableCapacity(int maxCapacity) {
        dynamicTable.setCapacity(maxCapacity);
    }
}
