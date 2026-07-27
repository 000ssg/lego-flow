package ssg.legoflow.http3.qpack;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * QPACK header encoder as defined in RFC 9204.
 *
 * <p>Encodes header field lists into QPACK wire format using static table
 * lookup, dynamic table insertion, and literal encoding. Supports Huffman
 * encoding for string values.</p>
 *
 * <p>The encoder produces a header block prefix (Required Insert Count and
 * Delta Base) followed by encoded header field representations:
 * indexed field lines, literal field lines with name reference, or
 * literal field lines with literal name.</p>
 *
 * <p>Dynamic table insertion is controlled by {@link #setUseDynamicTable(boolean)}.
 * When enabled, the encoder inserts new header fields into the dynamic table
 * and references them in subsequent encodes for improved compression.</p>
 *
 * @since 1.0.0
 */
public class QpackEncoder {

    private final QpackDynamicTable dynamicTable;
    private boolean useHuffman = true;
    private boolean useDynamicTable = false;
    private final ByteArrayOutputStream encoderInstructions = new ByteArrayOutputStream();

    /**
     * Creates a new encoder with default dynamic table capacity (4096 bytes).
     *
     * @since 1.0.0
     */
    public QpackEncoder() {
        this(4096);
    }

    /**
     * Creates a new encoder with the given dynamic table capacity.
     *
     * @param maxTableCapacity the maximum dynamic table capacity in bytes
     * @since 1.0.0
     */
    public QpackEncoder(int maxTableCapacity) {
        this.dynamicTable = new QpackDynamicTable(maxTableCapacity);
    }

    /**
     * Sets whether to use Huffman encoding for string values.
     *
     * @param useHuffman {@code true} to enable Huffman encoding
     * @since 1.0.0
     */
    public void setUseHuffman(boolean useHuffman) {
        this.useHuffman = useHuffman;
    }

    /**
     * Sets whether to use the dynamic table for encoding.
     *
     * <p>When enabled, the encoder inserts header fields that are not found
     * in the static table into the dynamic table and references them in
     * subsequent encodes. Encoder instructions are accumulated and can be
     * retrieved via {@link #drainEncoderInstructions()}.</p>
     *
     * @param useDynamicTable {@code true} to enable dynamic table insertion
     * @since 1.0.0
     */
    public void setUseDynamicTable(boolean useDynamicTable) {
        this.useDynamicTable = useDynamicTable;
    }

    /**
     * Returns whether the dynamic table is used for encoding.
     *
     * @return {@code true} if dynamic table insertion is enabled
     * @since 1.0.0
     */
    public boolean isUseDynamicTable() {
        return useDynamicTable;
    }

    /**
     * Encodes a list of header fields into QPACK wire format.
     *
     * <p>When dynamic table insertion is enabled, new headers not found in
     * either table are inserted into the dynamic table. The Required Insert Count
     * and Delta Base in the header block prefix are set accordingly.</p>
     *
     * @param headers the header fields to encode
     * @return a {@link ByteBuffer} containing the encoded header block
     * @since 1.0.0
     */
    public ByteBuffer encode(List<Map.Entry<String, String>> headers) {
        var fieldData = new ByteArrayOutputStream();
        int maxAbsoluteIndex = -1;

        for (var header : headers) {
            int usedAbsIdx = encodeHeader(fieldData, header.getKey(), header.getValue());
            if (usedAbsIdx > maxAbsoluteIndex) {
                maxAbsoluteIndex = usedAbsIdx;
            }
        }

        // Build header block prefix
        var out = new ByteArrayOutputStream();
        int requiredInsertCount = dynamicTable.computeRequiredInsertCount(maxAbsoluteIndex);
        int encodedRIC = dynamicTable.encodeRequiredInsertCount(requiredInsertCount);
        encodeInteger(out, encodedRIC, 8, 0x00);

        // Delta Base: sign=0 (non-negative), value = requiredInsertCount - base
        // With base = requiredInsertCount, delta = 0
        encodeInteger(out, 0, 7, 0x00);

        // Append field data
        out.writeBytes(fieldData.toByteArray());

        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Encodes headers from name-value pair arrays.
     *
     * @param nameValuePairs alternating name/value strings
     * @return a {@link ByteBuffer} containing the encoded header block
     * @throws IllegalArgumentException if the array length is not even
     * @since 1.0.0
     */
    public ByteBuffer encodeHeaderList(String... nameValuePairs) {
        if (nameValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Name-value pairs must be even");
        }
        var headers = new java.util.ArrayList<Map.Entry<String, String>>();
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            headers.add(new AbstractMap.SimpleEntry<>(nameValuePairs[i], nameValuePairs[i + 1]));
        }
        return encode(headers);
    }

    /**
     * Generates a Set Dynamic Table Capacity encoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.3.1) signals to the decoder
     * that the encoder's dynamic table capacity has changed.</p>
     *
     * @param capacity the new dynamic table capacity
     * @return a {@link ByteBuffer} containing the encoder instruction
     * @since 1.0.0
     */
    public ByteBuffer encodeSetDynamicTableCapacity(int capacity) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 001xxxxx
        encodeInteger(out, capacity, 5, 0x20);
        dynamicTable.setCapacity(capacity);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates an Insert With Name Reference encoder instruction for the static table.
     *
     * <p>This instruction (RFC 9204 section 4.3.2) inserts a new entry into the
     * dynamic table using a name from the static table.</p>
     *
     * @param staticIndex the static table index
     * @param value       the header field value
     * @return a {@link ByteBuffer} containing the encoder instruction
     * @since 1.0.0
     */
    public ByteBuffer encodeInsertWithStaticNameReference(int staticIndex, String value) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 1Txxxxxx (T=1 for static)
        encodeInteger(out, staticIndex, 6, 0xC0);
        encodeString(out, value);
        dynamicTable.insertWithStaticNameReference(staticIndex, value);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates an Insert With Name Reference encoder instruction for the dynamic table.
     *
     * <p>This instruction (RFC 9204 section 4.3.2) inserts a new entry into the
     * dynamic table using a name from the dynamic table.</p>
     *
     * @param relativeIndex the dynamic table relative index
     * @param value         the header field value
     * @return a {@link ByteBuffer} containing the encoder instruction
     * @since 1.0.0
     */
    public ByteBuffer encodeInsertWithDynamicNameReference(int relativeIndex, String value) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 1Txxxxxx (T=0 for dynamic)
        encodeInteger(out, relativeIndex, 6, 0x80);
        encodeString(out, value);
        dynamicTable.insertWithDynamicNameReference(relativeIndex, value);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates an Insert With Literal Name encoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.3.3) inserts a new entry with
     * both name and value specified literally.</p>
     *
     * @param name  the header field name
     * @param value the header field value
     * @return a {@link ByteBuffer} containing the encoder instruction
     * @since 1.0.0
     */
    public ByteBuffer encodeInsertWithLiteralName(String name, String value) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 01xxxxxx
        out.write(0x40);
        encodeString(out, name);
        encodeString(out, value);
        dynamicTable.insert(name, value);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Generates a Duplicate encoder instruction.
     *
     * <p>This instruction (RFC 9204 section 4.3.4) duplicates an existing
     * dynamic table entry to prevent its eviction.</p>
     *
     * @param relativeIndex the relative index of the entry to duplicate
     * @return a {@link ByteBuffer} containing the encoder instruction
     * @since 1.0.0
     */
    public ByteBuffer encodeDuplicate(int relativeIndex) {
        var out = new ByteArrayOutputStream();
        // Instruction format: 000xxxxx
        encodeInteger(out, relativeIndex, 5, 0x00);
        dynamicTable.duplicate(relativeIndex);
        return ByteBuffer.wrap(out.toByteArray());
    }

    /**
     * Drains accumulated encoder instructions that should be sent on the
     * QPACK encoder stream.
     *
     * <p>When dynamic table insertion is enabled during {@link #encode(List)},
     * the encoder accumulates instructions for new insertions. This method
     * retrieves and clears the accumulated instructions.</p>
     *
     * @return a {@link ByteBuffer} containing the encoder instructions, or empty if none
     * @since 1.0.0
     */
    public ByteBuffer drainEncoderInstructions() {
        if (encoderInstructions.size() == 0) {
            return ByteBuffer.allocate(0);
        }
        var data = ByteBuffer.wrap(encoderInstructions.toByteArray());
        encoderInstructions.reset();
        return data;
    }

    /**
     * Returns the absolute index for the given relative index.
     *
     * @param relativeIndex the relative index (0 = newest)
     * @return the absolute index
     * @since 1.0.0
     */
    private int relativeToAbsolute(int relativeIndex) {
        return dynamicTable.getInsertCount() - 1 - relativeIndex;
    }

    /**
     * Encodes a single header, potentially inserting into the dynamic table.
     *
     * @return the absolute index used from the dynamic table, or -1 if none
     */
    private int encodeHeader(ByteArrayOutputStream out, String name, String value) {
        // Try static table exact match
        int staticIdx = QpackStaticTable.findEntry(name, value);
        if (staticIdx >= 0) {
            // Indexed field line with static reference: 1T (T=1 for static) + index
            // Pattern: 11xxxxxx
            encodeInteger(out, staticIdx, 6, 0xC0);
            return -1;
        }

        // Try dynamic table exact match
        int dynamicIdx = dynamicTable.findEntry(name, value);
        if (dynamicIdx >= 0) {
            // Indexed field line with dynamic reference: 1T (T=0 for dynamic) + index
            // Pattern: 10xxxxxx
            encodeInteger(out, dynamicIdx, 6, 0x80);
            return relativeToAbsolute(dynamicIdx);
        }

        // Try static table name match
        int staticNameIdx = QpackStaticTable.findNameIndex(name);
        if (staticNameIdx >= 0) {
            if (useDynamicTable) {
                // Insert into dynamic table and generate encoder instruction
                var instrBuf = new ByteArrayOutputStream();
                encodeInteger(instrBuf, staticNameIdx, 6, 0xC0);
                encodeStringTo(instrBuf, value);
                encoderInstructions.writeBytes(instrBuf.toByteArray());
                dynamicTable.insertWithStaticNameReference(staticNameIdx, value);

                // Reference the newly inserted entry
                int newRelIdx = 0; // newest entry
                encodeInteger(out, newRelIdx, 6, 0x80);
                return relativeToAbsolute(newRelIdx);
            }
            // Literal with name reference (static): 01N1xxxx (N=0 never index)
            // Pattern: 0101xxxx — 4-bit prefix for name index
            encodeInteger(out, staticNameIdx, 4, 0x50);
            encodeString(out, value);
            return -1;
        }

        // Try dynamic table name match
        int dynamicNameIdx = dynamicTable.findNameIndex(name);
        if (dynamicNameIdx >= 0) {
            if (useDynamicTable) {
                // Insert with dynamic name reference
                var instrBuf = new ByteArrayOutputStream();
                encodeInteger(instrBuf, dynamicNameIdx, 6, 0x80);
                encodeStringTo(instrBuf, value);
                encoderInstructions.writeBytes(instrBuf.toByteArray());
                dynamicTable.insertWithDynamicNameReference(dynamicNameIdx, value);

                int newRelIdx = 0;
                encodeInteger(out, newRelIdx, 6, 0x80);
                return relativeToAbsolute(newRelIdx);
            }
            // Literal with name reference (dynamic): 01N0xxxx
            // Pattern: 0100xxxx — 4-bit prefix for name index
            encodeInteger(out, dynamicNameIdx, 4, 0x40);
            encodeString(out, value);
            return relativeToAbsolute(dynamicNameIdx);
        }

        if (useDynamicTable) {
            // Insert with literal name
            var instrBuf = new ByteArrayOutputStream();
            instrBuf.write(0x40);
            encodeStringTo(instrBuf, name);
            encodeStringTo(instrBuf, value);
            encoderInstructions.writeBytes(instrBuf.toByteArray());
            dynamicTable.insert(name, value);

            int newRelIdx = 0;
            encodeInteger(out, newRelIdx, 6, 0x80);
            return relativeToAbsolute(newRelIdx);
        }

        // Literal with literal name: 001Nxxxx
        // Pattern: 0010xxxx (N=0)
        out.write(0x20);
        encodeString(out, name);
        encodeString(out, value);
        return -1;
    }

    /**
     * Encodes an integer using the QPACK/HPACK integer encoding.
     *
     * @param out        the output stream
     * @param value      the integer value
     * @param prefixBits the number of prefix bits (1-8)
     * @param prefix     the prefix byte pattern
     * @since 1.0.0
     */
    public static void encodeInteger(ByteArrayOutputStream out, int value, int prefixBits, int prefix) {
        int maxPrefix = (1 << prefixBits) - 1;
        if (value < maxPrefix) {
            out.write(prefix | value);
        } else {
            out.write(prefix | maxPrefix);
            value -= maxPrefix;
            while (value >= 128) {
                out.write((value & 0x7F) | 0x80);
                value >>= 7;
            }
            out.write(value);
        }
    }

    /**
     * Encodes a string value, optionally using Huffman coding.
     *
     * @param out   the output stream
     * @param value the string to encode
     * @since 1.0.0
     */
    public void encodeString(ByteArrayOutputStream out, String value) {
        encodeStringTo(out, value);
    }

    /**
     * Internal string encoding to avoid overrideability issues.
     */
    private void encodeStringTo(ByteArrayOutputStream out, String value) {
        byte[] raw = value.getBytes(StandardCharsets.UTF_8);
        if (useHuffman) {
            byte[] huffmanEncoded = QpackHuffman.encode(raw);
            if (huffmanEncoded.length < raw.length) {
                encodeInteger(out, huffmanEncoded.length, 7, 0x80);
                out.writeBytes(huffmanEncoded);
                return;
            }
        }
        encodeInteger(out, raw.length, 7, 0x00);
        out.writeBytes(raw);
    }

    /**
     * Returns the dynamic table used by this encoder.
     *
     * @return the dynamic table
     * @since 1.0.0
     */
    public QpackDynamicTable getDynamicTable() {
        return dynamicTable;
    }

    /**
     * Sets the maximum dynamic table capacity.
     *
     * @param maxCapacity the new maximum capacity
     * @since 1.0.0
     */
    public void setMaxTableCapacity(int maxCapacity) {
        dynamicTable.setCapacity(maxCapacity);
    }
}
