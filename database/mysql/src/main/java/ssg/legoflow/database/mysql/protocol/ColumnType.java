package ssg.legoflow.database.mysql.protocol;

/**
 * MySQL column (field) types.
 *
 * <p>These type codes are used in ColumnDefinition packets and in the binary
 * protocol to identify how column values are encoded.
 *
 * @since 0.1.0
 */
public enum ColumnType {

    /** DECIMAL type. */
    DECIMAL(0x00),

    /** TINYINT type (1 byte). */
    TINY(0x01),

    /** SMALLINT type (2 bytes). */
    SHORT(0x02),

    /** INT type (4 bytes). */
    LONG(0x03),

    /** FLOAT type (4 bytes). */
    FLOAT(0x04),

    /** DOUBLE type (8 bytes). */
    DOUBLE(0x05),

    /** NULL type. */
    NULL(0x06),

    /** TIMESTAMP type. */
    TIMESTAMP(0x07),

    /** BIGINT type (8 bytes). */
    LONGLONG(0x08),

    /** MEDIUMINT type (3 bytes). */
    INT24(0x09),

    /** DATE type. */
    DATE(0x0A),

    /** TIME type. */
    TIME(0x0B),

    /** DATETIME type. */
    DATETIME(0x0C),

    /** YEAR type (2 bytes). */
    YEAR(0x0D),

    /** VARCHAR type. */
    VARCHAR(0x0F),

    /** BIT type. */
    BIT(0x10),

    /** JSON type (MySQL 5.7.8+). */
    JSON(0xF5),

    /** NEWDECIMAL type. */
    NEWDECIMAL(0xF6),

    /** ENUM type. */
    ENUM(0xF7),

    /** SET type. */
    SET(0xF8),

    /** TINYBLOB type. */
    TINY_BLOB(0xF9),

    /** MEDIUMBLOB type. */
    MEDIUM_BLOB(0xFA),

    /** LONGBLOB type. */
    LONG_BLOB(0xFB),

    /** BLOB type. */
    BLOB(0xFC),

    /** VAR_STRING type (VARCHAR, VARBINARY). */
    VAR_STRING(0xFD),

    /** STRING type (CHAR, BINARY). */
    STRING(0xFE);

    private final int code;

    ColumnType(int code) {
        this.code = code;
    }

    /**
     * Returns the MySQL type code.
     *
     * @return the numeric type code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the ColumnType for the given MySQL type code.
     *
     * @param code the numeric type code
     * @return the matching ColumnType
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static ColumnType fromCode(int code) {
        for (var type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown MySQL column type code: 0x" + Integer.toHexString(code));
    }

    /**
     * Returns true if this type is a numeric type.
     *
     * @return true for numeric types
     */
    public boolean isNumeric() {
        return switch (this) {
            case DECIMAL, TINY, SHORT, LONG, FLOAT, DOUBLE, LONGLONG, INT24, YEAR, NEWDECIMAL -> true;
            default -> false;
        };
    }

    /**
     * Returns true if this type is a string/text type.
     *
     * @return true for string types
     */
    public boolean isString() {
        return switch (this) {
            case VARCHAR, VAR_STRING, STRING, ENUM, SET, JSON -> true;
            default -> false;
        };
    }

    /**
     * Returns true if this type is a binary/blob type.
     *
     * @return true for blob types
     */
    public boolean isBlob() {
        return switch (this) {
            case TINY_BLOB, MEDIUM_BLOB, LONG_BLOB, BLOB -> true;
            default -> false;
        };
    }

    /**
     * Returns true if this type is a temporal type.
     *
     * @return true for temporal types
     */
    public boolean isTemporal() {
        return switch (this) {
            case TIMESTAMP, DATE, TIME, DATETIME, YEAR -> true;
            default -> false;
        };
    }
}
