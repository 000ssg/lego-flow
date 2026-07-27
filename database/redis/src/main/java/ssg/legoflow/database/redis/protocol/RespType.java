package ssg.legoflow.database.redis.protocol;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sealed interface representing all RESP2 and RESP3 wire-protocol types.
 *
 * <p>Each permitted record corresponds to a RESP type prefix byte:
 * <ul>
 *   <li>{@code +} Simple String</li>
 *   <li>{@code -} Simple Error</li>
 *   <li>{@code :} Integer</li>
 *   <li>{@code $} Bulk String</li>
 *   <li>{@code *} Array</li>
 *   <li>{@code _} Null (RESP3)</li>
 *   <li>{@code ,} Double (RESP3)</li>
 *   <li>{@code #} Boolean (RESP3)</li>
 *   <li>{@code (} Big number (RESP3)</li>
 *   <li>{@code !} Blob error (RESP3)</li>
 *   <li>{@code =} Verbatim string (RESP3)</li>
 *   <li>{@code %} Map (RESP3)</li>
 *   <li>{@code ~} Set (RESP3)</li>
 *   <li>{@code |} Attribute (RESP3)</li>
 *   <li>{@code >} Push (RESP3)</li>
 * </ul>
 *
 * @since 1.0.0
 */
public sealed interface RespType {

    // ---- RESP2 types ----

    /**
     * Simple string: {@code +OK\r\n}.
     *
     * @param value the string value
     */
    record SimpleString(String value) implements RespType {}

    /**
     * Simple error: {@code -ERR message\r\n}.
     *
     * @param prefix error type prefix (e.g. "ERR", "WRONGTYPE")
     * @param message error message
     */
    record Error(String prefix, String message) implements RespType {
        /**
         * Returns the full error string as "PREFIX message".
         *
         * @return full error text
         */
        public String fullMessage() {
            return prefix + " " + message;
        }
    }

    /**
     * Integer: {@code :1000\r\n}.
     *
     * @param value the long value
     */
    record Integer(long value) implements RespType {}

    /**
     * Bulk string: {@code $6\r\nfoobar\r\n}. Null bulk string when value is null.
     *
     * @param value the bytes, or null for null bulk string
     */
    record BulkString(byte[] value) implements RespType {
        /**
         * Returns the value as a UTF-8 string, or null if the bulk string is null.
         *
         * @return string value or null
         */
        public String asString() {
            return value == null ? null : new String(value, java.nio.charset.StandardCharsets.UTF_8);
        }

        /**
         * Creates a BulkString from a UTF-8 string.
         *
         * @param s the string (may be null)
         * @return new BulkString
         */
        public static BulkString of(String s) {
            return new BulkString(s == null ? null : s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        /** Null bulk string sentinel. */
        public static final BulkString NULL = new BulkString(null);
    }

    /**
     * Array: {@code *N\r\n...}. Null array when elements is null.
     *
     * @param elements the array elements, or null for null array
     */
    record Array(List<RespType> elements) implements RespType {
        /** Null array sentinel. */
        public static final Array NULL = new Array(null);
    }

    // ---- RESP3 types ----

    /**
     * Null: {@code _\r\n} (RESP3 only).
     */
    record Null() implements RespType {
        /** Singleton null instance. */
        public static final Null INSTANCE = new Null();
    }

    /**
     * Double: {@code ,1.23\r\n} (RESP3 only).
     *
     * @param value the double value
     */
    record RespDouble(double value) implements RespType {}

    /**
     * Boolean: {@code #t\r\n} or {@code #f\r\n} (RESP3 only).
     *
     * @param value true or false
     */
    record RespBoolean(boolean value) implements RespType {}

    /**
     * Big number: {@code (3492890328409238509324850943850943825024385\r\n} (RESP3 only).
     *
     * @param value the big integer value
     */
    record BigNumber(BigInteger value) implements RespType {}

    /**
     * Blob error: {@code !21\r\nSYNTAX invalid syntax\r\n} (RESP3 only).
     *
     * @param value the error bytes
     */
    record BlobError(byte[] value) implements RespType {
        /**
         * Returns the value as a UTF-8 string.
         *
         * @return string value
         */
        public String asString() {
            return new String(value, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * Verbatim string: {@code =15\r\ntxt:Some string\r\n} (RESP3 only).
     *
     * @param encoding the 3-character encoding type (e.g. "txt", "mkd")
     * @param value the string content
     */
    record VerbatimString(String encoding, String value) implements RespType {}

    /**
     * Map: {@code %N\r\n...} (RESP3 only).
     *
     * @param entries ordered map entries
     */
    record RespMap(Map<RespType, RespType> entries) implements RespType {}

    /**
     * Set: {@code ~N\r\n...} (RESP3 only).
     *
     * @param elements set elements
     */
    record RespSet(Set<RespType> elements) implements RespType {}

    /**
     * Attribute: {@code |N\r\n...} (RESP3 metadata, wraps a subsequent value).
     *
     * @param attributes the attribute map
     */
    record Attribute(Map<RespType, RespType> attributes) implements RespType {}

    /**
     * Push: {@code >N\r\n...} (RESP3 server push, e.g. pub/sub notifications).
     *
     * @param elements the push message elements
     */
    record Push(List<RespType> elements) implements RespType {}
}
