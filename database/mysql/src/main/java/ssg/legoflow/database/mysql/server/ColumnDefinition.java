package ssg.legoflow.database.mysql.server;

import ssg.legoflow.database.mysql.protocol.ColumnType;
import ssg.legoflow.database.mysql.protocol.LengthEncodedString;
import ssg.legoflow.database.mysql.common.Charset;

import java.nio.ByteBuffer;

/**
 * MySQL ColumnDefinition (COM_QUERY response, column metadata).
 *
 * <p>Describes a single column in a result set, including catalog, schema,
 * table, column name, character set, length, type, flags, and decimals.
 *
 * @param catalog the catalog name (always "def" in MySQL)
 * @param schema the database/schema name
 * @param table the virtual table name (alias)
 * @param orgTable the original table name
 * @param name the column name (alias)
 * @param orgName the original column name
 * @param charset the character set ID
 * @param columnLength the maximum column length
 * @param columnType the column type
 * @param flags the column flags
 * @param decimals number of decimal places
 * @since 0.1.0
 */
public record ColumnDefinition(
        String catalog,
        String schema,
        String table,
        String orgTable,
        String name,
        String orgName,
        int charset,
        long columnLength,
        ColumnType columnType,
        int flags,
        int decimals
) {

    // Column flags
    /** Column cannot be NULL. */
    public static final int NOT_NULL_FLAG = 1;
    /** Column is part of primary key. */
    public static final int PRI_KEY_FLAG = 1 << 1;
    /** Column is part of unique key. */
    public static final int UNIQUE_KEY_FLAG = 1 << 2;
    /** Column has BLOB type. */
    public static final int BLOB_FLAG = 1 << 4;
    /** Column is unsigned. */
    public static final int UNSIGNED_FLAG = 1 << 5;
    /** Column is auto-increment. */
    public static final int AUTO_INCREMENT_FLAG = 1 << 9;
    /** Column is a numeric type. */
    public static final int NUM_FLAG = 1 << 15;

    /**
     * Encodes this column definition as payload bytes.
     *
     * @return the encoded payload
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(512);

        LengthEncodedString.write(buf, catalog);
        LengthEncodedString.write(buf, schema);
        LengthEncodedString.write(buf, table);
        LengthEncodedString.write(buf, orgTable);
        LengthEncodedString.write(buf, name);
        LengthEncodedString.write(buf, orgName);

        // length of fixed-length fields [0C]
        buf.put((byte) 0x0C);

        // character set (2 bytes LE)
        buf.put((byte) (charset & 0xFF));
        buf.put((byte) ((charset >> 8) & 0xFF));

        // column length (4 bytes LE)
        buf.put((byte) (columnLength & 0xFF));
        buf.put((byte) ((columnLength >> 8) & 0xFF));
        buf.put((byte) ((columnLength >> 16) & 0xFF));
        buf.put((byte) ((columnLength >> 24) & 0xFF));

        // column type (1 byte)
        buf.put((byte) columnType.code());

        // flags (2 bytes LE)
        buf.put((byte) (flags & 0xFF));
        buf.put((byte) ((flags >> 8) & 0xFF));

        // decimals (1 byte)
        buf.put((byte) decimals);

        // filler (2 bytes)
        buf.put((byte) 0);
        buf.put((byte) 0);

        buf.flip();
        var result = new byte[buf.remaining()];
        buf.get(result);
        return result;
    }

    /**
     * Decodes a column definition from payload bytes.
     *
     * @param payload the packet payload
     * @return the decoded column definition
     */
    public static ColumnDefinition decode(byte[] payload) {
        var buf = ByteBuffer.wrap(payload);

        String catalog = LengthEncodedString.read(buf);
        String schema = LengthEncodedString.read(buf);
        String table = LengthEncodedString.read(buf);
        String orgTable = LengthEncodedString.read(buf);
        String name = LengthEncodedString.read(buf);
        String orgName = LengthEncodedString.read(buf);

        buf.get(); // skip 0x0C

        int charset = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
        long columnLength = (buf.get() & 0xFFL) | ((buf.get() & 0xFFL) << 8)
                | ((buf.get() & 0xFFL) << 16) | ((buf.get() & 0xFFL) << 24);
        int typeCode = buf.get() & 0xFF;
        int flags = (buf.get() & 0xFF) | ((buf.get() & 0xFF) << 8);
        int decimals = buf.get() & 0xFF;

        return new ColumnDefinition(catalog, schema, table, orgTable, name, orgName,
                charset, columnLength, ColumnType.fromCode(typeCode), flags, decimals);
    }

    /**
     * Creates a simple column definition with defaults.
     *
     * @param name the column name
     * @param type the column type
     * @param length the column length
     * @return the column definition
     */
    public static ColumnDefinition of(String name, ColumnType type, int length) {
        return new ColumnDefinition("def", "", "", "", name, name,
                Charset.DEFAULT_CHARSET_ID, length, type, 0, 0);
    }

    /**
     * Creates a column definition with schema and table info.
     *
     * @param schema the database name
     * @param table the table name
     * @param name the column name
     * @param type the column type
     * @param length the column length
     * @param flags the column flags
     * @return the column definition
     */
    public static ColumnDefinition of(String schema, String table, String name,
                                       ColumnType type, int length, int flags) {
        return new ColumnDefinition("def", schema, table, table, name, name,
                Charset.DEFAULT_CHARSET_ID, length, type, flags, 0);
    }
}
