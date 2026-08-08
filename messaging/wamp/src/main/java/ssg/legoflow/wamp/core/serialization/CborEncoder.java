package ssg.legoflow.wamp.core.serialization;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Encodes Java objects to CBOR (Concise Binary Object Representation) format.
 * Implements RFC 8949 from scratch without external libraries.
 *
 * <p>CBOR uses major types 0-7 with additional info encoding lengths.
 * Supported: integers, floats, strings, byte arrays, arrays, maps, null, booleans.</p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8949">RFC 8949 — CBOR</a>
 * @since 0.1.0
 */
public class CborEncoder {

    // Major types
    private static final int MAJOR_UNSIGNED = 0;   // 0 << 5
    private static final int MAJOR_NEGATIVE = 1;   // 1 << 5 = 0x20
    private static final int MAJOR_BYTES    = 2;   // 2 << 5 = 0x40
    private static final int MAJOR_TEXT     = 3;   // 3 << 5 = 0x60
    private static final int MAJOR_ARRAY    = 4;   // 4 << 5 = 0x80
    private static final int MAJOR_MAP      = 5;   // 5 << 5 = 0xa0
    private static final int MAJOR_SIMPLE   = 7;   // 7 << 5 = 0xe0

    private final ByteArrayOutputStream out;

    /**
     * Creates a new CBOR encoder.
     */
    public CborEncoder() {
        this.out = new ByteArrayOutputStream(256);
    }

    /**
     * Encodes an object to CBOR format and returns the bytes.
     *
     * @param obj the object to encode
     * @return the CBOR encoded bytes
     * @throws IllegalArgumentException if the object type is not supported
     */
    public byte[] encode(Object obj) {
        out.reset();
        writeObject(obj);
        return out.toByteArray();
    }

    /**
     * Writes an object to the internal buffer.
     *
     * @param obj the object to write
     */
    @SuppressWarnings("unchecked")
    public void writeObject(Object obj) {
        if (obj == null) {
            writeNull();
        } else if (obj instanceof Boolean b) {
            writeBoolean(b);
        } else if (obj instanceof Integer i) {
            writeInt(i);
        } else if (obj instanceof Long l) {
            writeLong(l);
        } else if (obj instanceof Float f) {
            writeFloat(f);
        } else if (obj instanceof Double d) {
            writeDouble(d);
        } else if (obj instanceof String s) {
            writeString(s);
        } else if (obj instanceof byte[] bytes) {
            writeBinary(bytes);
        } else if (obj instanceof List<?> list) {
            writeArray((List<Object>) list);
        } else if (obj instanceof Map<?, ?> map) {
            writeMap((Map<String, Object>) map);
        } else if (obj instanceof Number n) {
            writeLong(n.longValue());
        } else {
            throw new IllegalArgumentException("Unsupported type: " + obj.getClass().getName());
        }
    }

    /**
     * Writes a null (CBOR simple value 22).
     */
    public void writeNull() {
        out.write((MAJOR_SIMPLE << 5) | 22); // 0xf6
    }

    /**
     * Writes a boolean value (CBOR simple values 20/21).
     *
     * @param value the boolean value
     */
    public void writeBoolean(boolean value) {
        out.write((MAJOR_SIMPLE << 5) | (value ? 21 : 20)); // 0xf5 or 0xf4
    }

    /**
     * Writes an integer value.
     *
     * @param value the integer value
     */
    public void writeInt(int value) {
        writeLong(value);
    }

    /**
     * Writes a long value. Positive values use major type 0, negative use major type 1.
     *
     * @param value the long value
     */
    public void writeLong(long value) {
        if (value >= 0) {
            writeTypedInt(MAJOR_UNSIGNED, value);
        } else {
            // CBOR negative: -1 - n, so encode -(value+1) = -value - 1
            writeTypedInt(MAJOR_NEGATIVE, -1L - value);
        }
    }

    /**
     * Writes a 32-bit float value (CBOR major type 7, additional info 26).
     *
     * @param value the float value
     */
    public void writeFloat(float value) {
        out.write((MAJOR_SIMPLE << 5) | 26); // 0xfa
        int bits = Float.floatToIntBits(value);
        out.write((bits >> 24) & 0xff);
        out.write((bits >> 16) & 0xff);
        out.write((bits >> 8) & 0xff);
        out.write(bits & 0xff);
    }

    /**
     * Writes a 64-bit double value (CBOR major type 7, additional info 27).
     *
     * @param value the double value
     */
    public void writeDouble(double value) {
        out.write((MAJOR_SIMPLE << 5) | 27); // 0xfb
        long bits = Double.doubleToLongBits(value);
        writeRawLong(bits);
    }

    /**
     * Writes a text string (CBOR major type 3).
     *
     * @param value the string value
     */
    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeTypedInt(MAJOR_TEXT, bytes.length);
        out.write(bytes, 0, bytes.length);
    }

    /**
     * Writes a byte string (CBOR major type 2).
     *
     * @param value the byte array
     */
    public void writeBinary(byte[] value) {
        writeTypedInt(MAJOR_BYTES, value.length);
        out.write(value, 0, value.length);
    }

    /**
     * Writes an array (CBOR major type 4).
     *
     * @param list the list of elements
     */
    public void writeArray(List<Object> list) {
        writeTypedInt(MAJOR_ARRAY, list.size());
        for (var item : list) {
            writeObject(item);
        }
    }

    /**
     * Writes a map (CBOR major type 5).
     *
     * @param map the map to write
     */
    public void writeMap(Map<String, Object> map) {
        writeTypedInt(MAJOR_MAP, map.size());
        for (var entry : map.entrySet()) {
            writeString(entry.getKey());
            writeObject(entry.getValue());
        }
    }

    /**
     * Writes a major type with an integer argument using the smallest CBOR encoding.
     */
    private void writeTypedInt(int majorType, long value) {
        int mt = majorType << 5;
        if (value <= 23) {
            out.write(mt | (int) value);
        } else if (value <= 0xff) {
            out.write(mt | 24);
            out.write((int) value);
        } else if (value <= 0xffff) {
            out.write(mt | 25);
            out.write((int) (value >> 8) & 0xff);
            out.write((int) value & 0xff);
        } else if (value <= 0xffffffffL) {
            out.write(mt | 26);
            out.write((int) (value >> 24) & 0xff);
            out.write((int) (value >> 16) & 0xff);
            out.write((int) (value >> 8) & 0xff);
            out.write((int) value & 0xff);
        } else {
            out.write(mt | 27);
            writeRawLong(value);
        }
    }

    private void writeRawLong(long value) {
        out.write((int) (value >> 56) & 0xff);
        out.write((int) (value >> 48) & 0xff);
        out.write((int) (value >> 40) & 0xff);
        out.write((int) (value >> 32) & 0xff);
        out.write((int) (value >> 24) & 0xff);
        out.write((int) (value >> 16) & 0xff);
        out.write((int) (value >> 8) & 0xff);
        out.write((int) value & 0xff);
    }
}
