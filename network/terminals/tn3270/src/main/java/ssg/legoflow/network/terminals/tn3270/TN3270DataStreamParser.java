package ssg.legoflow.network.terminals.tn3270;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 3270 data stream parser.
 *
 * <p>Parses the binary 3270 data stream format (IBM 3270 Emulation Communication
 * Interface). The data stream contains field-level data with attributes that
 * must be applied to a terminal screen.
 *
 * <p>Key features:
 * <ul>
 *   <li>Parses length-encoded field data (1-3 byte length fields)</li>
 *   <li>Identifies control functions (PPI, TSS, RTS, ECD, etc.)</li>
 *   <li>Extracts field attribute pairs (primary + secondary)</li>
 *   <li>Separates keyboard data from screen data</li>
 * </ul>
 *
 * <p>3270 data stream format:
 * <pre>
 *   [keyboard data: 32 bytes]
 *   [control function bytes (0x80-0xFF)]
 *   [field length (1-3 bytes with continuation)]
 *     [field attributes (2 bytes)]
 *     [field data bytes]
 *   [field length]
 *     [field attributes]
 *     [field data]
 *   ...
 * </pre>
 *
 * @since 0.2.0
 */
public final class TN3270DataStreamParser {

    /** Maximum keyboard area size in bytes. */
    public static final int KEYBOARD_SIZE = 32;

    /** Control function bit mask (high bit set = control byte). */
    private static final int CF_MASK = 0x80;

    /** Control function: Print Page Indicator (end of data stream). */
    private static final int CF_PPI = 0x80;

    /** Control function: Reset to Screen. */
    private static final int CF_RTS = 0x81;

    /** Control function: Top of Screen Screen. */
    private static final int CF_TSS = 0x82;

    /** Control function: Erase Change Detection. */
    private static final int CF_ECD = 0x83;

    /** Control function: Erase All Unchanged. */
    private static final int CF_UNDO = 0x84;

    /** Control function: Flash Screen. */
    private static final int CF_FLASH = 0x85;

    /** Control function: Request Keyboard. */
    private static final int CF_RK = 0x87;

    /** Control function: Attention (ATN). */
    private static final int CF_ATN = 0x88;

    private final List<DataStreamRecord> records = Collections.synchronizedList(new ArrayList<>());

    /**
     * Parse a 3270 data stream.
     *
     * @param data the binary data stream
     * @return list of parsed records
     */
    public List<DataStreamRecord> parse(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        records.clear();
        parseData(data, 0, data.length);
        return List.copyOf(records);
    }

    /**
     * Parse a 3270 data stream from a specific offset.
     *
     * @param data the binary data stream
     * @param offset the starting offset
     * @param length the number of bytes to parse
     */
    public void parseData(byte[] data, int offset, int length) {
        Objects.requireNonNull(data, "data must not be null");
        int pos = offset;
        int end = offset + length;

        // First 32 bytes are keyboard data
        int keyboardEnd = Math.min(pos + KEYBOARD_SIZE, end);
        byte[] keyboardData = new byte[keyboardEnd - pos];
        System.arraycopy(data, pos, keyboardData, 0, keyboardData.length);
        records.add(new KeyboardDataRecord(keyboardData));
        pos = keyboardEnd;

        // Parse remaining data stream records
        while (pos < end) {
            int byteVal = data[pos] & 0xFF;

            if (isControlFunction(byteVal)) {
                parseControlFunction(data, pos, end);
                // Control functions consume variable bytes; the helper advances pos
                // But since we can't modify local vars from inner scope, we re-parse
                break; // Recursively handle
            } else {
                // Field data: length (1-3 bytes) + data
                int[] lengthInfo = readLength(data, pos, end);
                int fieldLength = lengthInfo[0];
                int lengthBytesConsumed = lengthInfo[1];
                pos += lengthBytesConsumed;

                if (pos + fieldLength > end) {
                    // Truncated record — ignore
                    break;
                }

                // Parse field attributes (2 bytes) — always present in data stream
                byte primaryAttr = 0x00;
                byte secondaryAttr = 0x00;

                if (pos < end) {
                    primaryAttr = data[pos++];
                }
                if (pos < end) {
                    secondaryAttr = data[pos++];
                }

                // 3270 field length includes the 2 attribute bytes
                int fieldDataBytes = fieldLength - 2;
                if (fieldDataBytes < 0) {
                    fieldDataBytes = 0;
                }

                if (fieldDataBytes > 0 && pos + fieldDataBytes <= end) {
                    byte[] fieldData = new byte[fieldDataBytes];
                    System.arraycopy(data, pos, fieldData, 0, fieldDataBytes);
                    pos += fieldDataBytes;

                    TN3270FieldAttr attr;
                    if (primaryAttr == 0x00 && secondaryAttr == 0x00) {
                        attr = TN3270FieldAttr.NORMAL;
                    } else {
                        attr = new TN3270FieldAttr(
                            primaryAttr & 0xFF,
                            secondaryAttr & 0xFF,
                            "inline"
                        );
                    }
                    records.add(new FieldDataRecord(fieldData, attr));
                }
            }
        }
    }

    private boolean isControlFunction(int b) {
        return (b & CF_MASK) != 0;
    }

    private void parseControlFunction(byte[] data, int pos, int end) {
        int byteVal = data[pos] & 0xFF;

        if (byteVal == CF_PPI) {
            // PPI: marks end of data stream — consume but don't store
            records.add(ControlRecord.PPI);
            return;
        } else if (byteVal == CF_RTS) {
            records.add(ControlRecord.RTS);
        } else if (byteVal == CF_TSS) {
            records.add(ControlRecord.TSS);
        } else if (byteVal == CF_ECD) {
            records.add(ControlRecord.ECD);
        } else if (byteVal == CF_UNDO) {
            records.add(ControlRecord.UNDO);
        } else if (byteVal == CF_FLASH) {
            records.add(ControlRecord.FLASH);
        } else if (byteVal == CF_RK) {
            records.add(ControlRecord.RK);
        } else if (byteVal == CF_ATN) {
            records.add(ControlRecord.ATN);
        } else {
            records.add(new UnknownControlRecord(byteVal));
        }
    }

    /**
     * Read a 3270 field length (1-3 bytes with continuation bit).
     *
     * <p>A length byte with bit 7 set indicates continuation — the next byte
     * is also part of the length. This allows lengths up to 4095 bytes (12 bits).
     *
     * @return int[2]: [length, bytesConsumed]
     */
    private static int[] readLength(byte[] data, int pos, int end) {
        int length = 0;
        int bytesConsumed = 0;

        while (pos + bytesConsumed < end) {
            int b = data[pos + bytesConsumed] & 0xFF;
            length = (length << 7) | (b & 0x7F);
            bytesConsumed++;

            if ((b & 0x80) == 0) {
                break; // Last byte (continuation bit not set)
            }
        }

        return new int[]{length, bytesConsumed};
    }

    /** Get the parsed records. */
    public List<DataStreamRecord> getRecords() {
        return List.copyOf(records);
    }

    /**
     * Base record type in a 3270 data stream.
     */
    public interface DataStreamRecord {
        int kind();
    }

    /**
     * Keyboard data record — first 32 bytes of a 3270 data stream.
     */
    public record KeyboardDataRecord(byte[] data) implements DataStreamRecord {
        @Override
        public int kind() { return 0; }
    }

    /**
     * Field data record — screen data with attributes.
     */
    public record FieldDataRecord(byte[] data, TN3270FieldAttr attr) implements DataStreamRecord {
        @Override
        public int kind() { return 1; }
    }

    /**
     * Control function record — control bytes in the data stream.
     */
    public interface ControlRecord extends DataStreamRecord {
        @Override
        int kind();

        /** Print Page Indicator (end of data stream). */
        ControlRecord PPI = new ControlRecord() {
            @Override public int kind() { return 10; }
        };

        /** Reset to Screen. */
        ControlRecord RTS = new ControlRecord() {
            @Override public int kind() { return 11; }
        };

        /** Top of Screen Screen. */
        ControlRecord TSS = new ControlRecord() {
            @Override public int kind() { return 12; }
        };

        /** Erase Change Detection. */
        ControlRecord ECD = new ControlRecord() {
            @Override public int kind() { return 13; }
        };

        /** Erase All Unchanged. */
        ControlRecord UNDO = new ControlRecord() {
            @Override public int kind() { return 14; }
        };

        /** Flash Screen. */
        ControlRecord FLASH = new ControlRecord() {
            @Override public int kind() { return 15; }
        };

        /** Request Keyboard. */
        ControlRecord RK = new ControlRecord() {
            @Override public int kind() { return 16; }
        };

        /** Attention. */
        ControlRecord ATN = new ControlRecord() {
            @Override public int kind() { return 17; }
        };
    }

    /**
     * Unknown control function record.
     */
    public record UnknownControlRecord(int controlByte) implements ControlRecord {
        @Override
        public int kind() { return -1; }
    }
}
