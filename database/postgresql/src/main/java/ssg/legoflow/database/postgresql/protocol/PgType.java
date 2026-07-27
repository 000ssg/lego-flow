package ssg.legoflow.database.postgresql.protocol;

/**
 * PostgreSQL type OIDs for common data types.
 *
 * <p>These OIDs are used in RowDescription messages to identify column types,
 * and in Parse messages to specify parameter types.
 *
 * @since 1.0.0
 */
public enum PgType {

    /** Boolean type. */
    BOOL(16, "bool"),

    /** 2-byte signed integer. */
    INT2(21, "int2"),

    /** 4-byte signed integer. */
    INT4(23, "int4"),

    /** 8-byte signed integer. */
    INT8(20, "int8"),

    /** Single-precision floating point. */
    FLOAT4(700, "float4"),

    /** Double-precision floating point. */
    FLOAT8(701, "float8"),

    /** Arbitrary-precision numeric. */
    NUMERIC(1700, "numeric"),

    /** Variable-length character string. */
    VARCHAR(1043, "varchar"),

    /** Fixed-length character string. */
    CHAR(1042, "char"),

    /** Variable-length text. */
    TEXT(25, "text"),

    /** Binary data. */
    BYTEA(17, "bytea"),

    /** Date (no time). */
    DATE(1082, "date"),

    /** Time without time zone. */
    TIME(1083, "time"),

    /** Timestamp without time zone. */
    TIMESTAMP(1114, "timestamp"),

    /** Timestamp with time zone. */
    TIMESTAMPTZ(1184, "timestamptz"),

    /** Time interval. */
    INTERVAL(1186, "interval"),

    /** UUID. */
    UUID(2950, "uuid"),

    /** JSON text. */
    JSON(114, "json"),

    /** JSON binary. */
    JSONB(3802, "jsonb"),

    /** XML data. */
    XML(142, "xml"),

    /** Object identifier. */
    OID(26, "oid"),

    /** Void (no return value). */
    VOID(2278, "void"),

    /** Unknown/unspecified type. */
    UNKNOWN(705, "unknown");

    private final int oid;
    private final String typeName;

    PgType(int oid, String typeName) {
        this.oid = oid;
        this.typeName = typeName;
    }

    /**
     * Returns the PostgreSQL OID for this type.
     *
     * @return the type OID
     */
    public int oid() {
        return oid;
    }

    /**
     * Returns the PostgreSQL type name.
     *
     * @return the type name
     */
    public String typeName() {
        return typeName;
    }

    /**
     * Returns the typical size in bytes, or -1 for variable-length types.
     *
     * @return the type size
     */
    public int typeSize() {
        return switch (this) {
            case BOOL -> 1;
            case INT2 -> 2;
            case INT4, FLOAT4, OID -> 4;
            case INT8, FLOAT8, TIMESTAMP, TIMESTAMPTZ, TIME, DATE -> 8;
            case INTERVAL -> 16;
            case UUID -> 16;
            default -> -1;
        };
    }

    /**
     * Finds a PgType by its OID.
     *
     * @param oid the type OID
     * @return the matching type, or {@link #UNKNOWN} if not found
     */
    public static PgType fromOid(int oid) {
        for (PgType type : values()) {
            if (type.oid == oid) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * Finds a PgType by its name (case-insensitive).
     *
     * @param name the type name
     * @return the matching type, or {@link #UNKNOWN} if not found
     */
    public static PgType fromName(String name) {
        String lower = name.toLowerCase();
        for (PgType type : values()) {
            if (type.typeName.equals(lower)) {
                return type;
            }
        }
        // Handle common aliases
        return switch (lower) {
            case "integer", "int" -> INT4;
            case "bigint" -> INT8;
            case "smallint" -> INT2;
            case "real" -> FLOAT4;
            case "double precision" -> FLOAT8;
            case "character varying" -> VARCHAR;
            case "character" -> CHAR;
            case "boolean" -> BOOL;
            default -> UNKNOWN;
        };
    }
}
