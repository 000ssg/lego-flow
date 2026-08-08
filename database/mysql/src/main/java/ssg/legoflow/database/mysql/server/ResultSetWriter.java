package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Writes MySQL result sets (text and binary protocol) to an output stream.
 *
 * <p>Handles the full result set packet sequence: column count, column
 * definitions, EOF (or OK with DEPRECATE_EOF), row data, and final EOF.
 *
 * @since 0.1.0
 */
public final class ResultSetWriter {

    private ResultSetWriter() {}

    /**
     * Writes a text protocol result set.
     *
     * @param out the output stream
     * @param columns the column definitions
     * @param rows the row data (each row is a list of string values, null for NULL)
     * @param capabilities the negotiated capabilities
     * @param startSeqId the starting sequence ID
     * @return the next sequence ID after all packets
     * @throws IOException if an I/O error occurs
     */
    public static int writeTextResultSet(OutputStream out,
                                          List<ColumnDefinition> columns,
                                          List<List<String>> rows,
                                          int capabilities,
                                          int startSeqId) throws IOException {
        int seqId = startSeqId;

        // Column count
        var countPayload = LengthEncodedInt.encode(columns.size());
        new MysqlPacket(seqId++, countPayload).writeTo(out);

        // Column definitions
        for (var col : columns) {
            new MysqlPacket(seqId++, col.encode()).writeTo(out);
        }

        // EOF after columns (unless DEPRECATE_EOF)
        if (!CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
        }

        // Rows
        for (var row : rows) {
            new MysqlPacket(seqId++, encodeTextRow(row)).writeTo(out);
        }

        // Final EOF (or OK with 0xFE header when DEPRECATE_EOF)
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            new MysqlPacket(seqId++, OkPacket.ok().encodeAsEof(capabilities)).writeTo(out);
        } else {
            new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
        }

        return seqId;
    }

    /**
     * Writes a text protocol result set from map-based rows.
     *
     * @param out the output stream
     * @param columns the column definitions
     * @param rows the row data as maps (column name to value)
     * @param capabilities the negotiated capabilities
     * @param startSeqId the starting sequence ID
     * @return the next sequence ID
     * @throws IOException if an I/O error occurs
     */
    public static int writeTextResultSetFromMaps(OutputStream out,
                                                  List<ColumnDefinition> columns,
                                                  List<Map<String, String>> rows,
                                                  int capabilities,
                                                  int startSeqId) throws IOException {
        var convertedRows = new java.util.ArrayList<List<String>>();
        for (var row : rows) {
            var values = new java.util.ArrayList<String>();
            for (var col : columns) {
                values.add(row.get(col.name()));
            }
            convertedRows.add(values);
        }
        return writeTextResultSet(out, columns, convertedRows, capabilities, startSeqId);
    }

    /**
     * Writes a binary protocol result set (for prepared statement execution).
     *
     * @param out the output stream
     * @param columns the column definitions
     * @param rows the row data (list of string values)
     * @param capabilities the negotiated capabilities
     * @param startSeqId the starting sequence ID
     * @return the next sequence ID
     * @throws IOException if an I/O error occurs
     */
    public static int writeBinaryResultSet(OutputStream out,
                                            List<ColumnDefinition> columns,
                                            List<List<String>> rows,
                                            int capabilities,
                                            int startSeqId) throws IOException {
        int seqId = startSeqId;

        // Column count
        var countPayload = LengthEncodedInt.encode(columns.size());
        new MysqlPacket(seqId++, countPayload).writeTo(out);

        // Column definitions
        for (var col : columns) {
            new MysqlPacket(seqId++, col.encode()).writeTo(out);
        }

        // EOF after columns
        if (!CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
        }

        // Binary rows
        for (var row : rows) {
            new MysqlPacket(seqId++, encodeBinaryRow(row, columns)).writeTo(out);
        }

        // Final EOF (or OK with 0xFE header when DEPRECATE_EOF)
        if (CapabilityFlags.hasCapability(capabilities, CapabilityFlags.CLIENT_DEPRECATE_EOF)) {
            new MysqlPacket(seqId++, OkPacket.ok().encodeAsEof(capabilities)).writeTo(out);
        } else {
            new MysqlPacket(seqId++, EofPacket.eof().encode(capabilities)).writeTo(out);
        }

        return seqId;
    }

    /**
     * Encodes a text protocol row as payload bytes.
     *
     * @param values the column values (null entries become 0xFB NULL markers)
     * @return the encoded row payload
     */
    public static byte[] encodeTextRow(List<String> values) {
        var buf = ByteBuffer.allocate(4096);
        for (var value : values) {
            if (value == null) {
                buf.put((byte) 0xFB); // NULL
            } else {
                LengthEncodedString.write(buf, value);
            }
        }
        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a text protocol row from payload bytes.
     *
     * @param payload the row payload
     * @param columnCount the number of columns
     * @return list of string values (null for NULL columns)
     */
    public static List<String> decodeTextRow(byte[] payload, int columnCount) {
        var buf = ByteBuffer.wrap(payload);
        var values = new java.util.ArrayList<String>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            int peek = buf.get(buf.position()) & 0xFF;
            if (peek == 0xFB) {
                buf.get(); // consume NULL marker
                values.add(null);
            } else {
                values.add(LengthEncodedString.read(buf));
            }
        }
        return values;
    }

    /**
     * Encodes a binary protocol row as payload bytes.
     *
     * @param values the column values
     * @param columns the column definitions
     * @return the encoded binary row payload
     */
    public static byte[] encodeBinaryRow(List<String> values, List<ColumnDefinition> columns) {
        int columnCount = columns.size();
        int nullBitmapLength = (columnCount + 7 + 2) / 8;

        var buf = ByteBuffer.allocate(8192);

        // Packet header (0x00 for binary row)
        buf.put((byte) 0x00);

        // NULL bitmap
        var nullBitmap = new byte[nullBitmapLength];
        for (int i = 0; i < columnCount; i++) {
            if (i < values.size() && values.get(i) == null) {
                int bytePos = (i + 2) / 8;
                int bitPos = (i + 2) % 8;
                nullBitmap[bytePos] |= (byte) (1 << bitPos);
            }
        }
        buf.put(nullBitmap);

        // Column values
        for (int i = 0; i < columnCount; i++) {
            String value = (i < values.size()) ? values.get(i) : null;
            if (value == null) continue;

            var type = columns.get(i).columnType();
            encodeBinaryValue(buf, value, type);
        }

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a binary protocol row from payload bytes.
     *
     * @param payload the row payload
     * @param columns the column definitions
     * @return list of string values (null for NULL columns)
     */
    public static List<String> decodeBinaryRow(byte[] payload, List<ColumnDefinition> columns) {
        var buf = ByteBuffer.wrap(payload);
        int columnCount = columns.size();

        buf.get(); // skip 0x00 header

        int nullBitmapLength = (columnCount + 7 + 2) / 8;
        var nullBitmap = new byte[nullBitmapLength];
        buf.get(nullBitmap);

        var values = new java.util.ArrayList<String>(columnCount);
        for (int i = 0; i < columnCount; i++) {
            int bytePos = (i + 2) / 8;
            int bitPos = (i + 2) % 8;
            boolean isNull = (nullBitmap[bytePos] & (1 << bitPos)) != 0;

            if (isNull) {
                values.add(null);
            } else {
                values.add(decodeBinaryValue(buf, columns.get(i).columnType()));
            }
        }
        return values;
    }

    private static void encodeBinaryValue(ByteBuffer buf, String value, ColumnType type) {
        switch (type) {
            case TINY -> buf.put((byte) Integer.parseInt(value));
            case SHORT, YEAR -> {
                int v = Integer.parseInt(value);
                buf.put((byte) (v & 0xFF));
                buf.put((byte) ((v >> 8) & 0xFF));
            }
            case LONG, INT24 -> {
                int v = Integer.parseInt(value);
                buf.put((byte) (v & 0xFF));
                buf.put((byte) ((v >> 8) & 0xFF));
                buf.put((byte) ((v >> 16) & 0xFF));
                buf.put((byte) ((v >> 24) & 0xFF));
            }
            case LONGLONG -> {
                long v = Long.parseLong(value);
                for (int j = 0; j < 8; j++) {
                    buf.put((byte) ((v >> (j * 8)) & 0xFF));
                }
            }
            case FLOAT -> {
                int bits = Float.floatToIntBits(Float.parseFloat(value));
                buf.put((byte) (bits & 0xFF));
                buf.put((byte) ((bits >> 8) & 0xFF));
                buf.put((byte) ((bits >> 16) & 0xFF));
                buf.put((byte) ((bits >> 24) & 0xFF));
            }
            case DOUBLE -> {
                long bits = Double.doubleToLongBits(Double.parseDouble(value));
                for (int j = 0; j < 8; j++) {
                    buf.put((byte) ((bits >> (j * 8)) & 0xFF));
                }
            }
            default -> LengthEncodedString.write(buf, value);
        }
    }

    private static String decodeBinaryValue(ByteBuffer buf, ColumnType type) {
        return switch (type) {
            case TINY -> String.valueOf(buf.get());
            case SHORT, YEAR -> {
                int v = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
                yield String.valueOf((short) v);
            }
            case LONG, INT24 -> {
                int v = (buf.get() & 0xFF)
                        | ((buf.get() & 0xFF) << 8)
                        | ((buf.get() & 0xFF) << 16)
                        | ((buf.get() & 0xFF) << 24);
                yield String.valueOf(v);
            }
            case LONGLONG -> {
                long v = 0;
                for (int j = 0; j < 8; j++) {
                    v |= (long) (buf.get() & 0xFF) << (j * 8);
                }
                yield String.valueOf(v);
            }
            case FLOAT -> {
                int bits = (buf.get() & 0xFF)
                        | ((buf.get() & 0xFF) << 8)
                        | ((buf.get() & 0xFF) << 16)
                        | ((buf.get() & 0xFF) << 24);
                yield String.valueOf(Float.intBitsToFloat(bits));
            }
            case DOUBLE -> {
                long bits = 0;
                for (int j = 0; j < 8; j++) {
                    bits |= (long) (buf.get() & 0xFF) << (j * 8);
                }
                yield String.valueOf(Double.longBitsToDouble(bits));
            }
            default -> LengthEncodedString.read(buf);
        };
    }
}
