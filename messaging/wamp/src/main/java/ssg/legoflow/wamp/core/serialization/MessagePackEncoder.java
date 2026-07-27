package ssg.legoflow.wamp.core.serialization;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Encodes Java objects to MessagePack binary format.
 * Implements the MessagePack specification from scratch without external libraries.
 *
 * <p>Supported types: {@code null}, {@code Boolean}, {@code Integer}, {@code Long},
 * {@code Float}, {@code Double}, {@code String}, {@code byte[]},
 * {@code List}, {@code Map}.</p>
 *
 * @see <a href="https://github.com/msgpack/msgpack/blob/master/spec.md">MessagePack spec</a>
 * @since 1.0.0
 */
public class MessagePackEncoder {

    private final ByteArrayOutputStream out;

    /**
     * Creates a new encoder.
     */
    public MessagePackEncoder() {
        this.out = new ByteArrayOutputStream(256);
    }

    /**
     * Creates a new encoder with the given initial capacity.
     *
     * @param initialCapacity the initial buffer capacity in bytes
     */
    public MessagePackEncoder(int initialCapacity) {
        this.out = new ByteArrayOutputStream(initialCapacity);
    }

    /**
     * Encodes an object to MessagePack format and returns the bytes.
     *
     * @param obj the object to encode
     * @return the MessagePack encoded bytes
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
            writeNil();
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
     * Writes a nil value.
     */
    public void writeNil() {
        out.write(0xc0);
    }

    /**
     * Writes a boolean value.
     *
     * @param value the boolean value
     */
    public void writeBoolean(boolean value) {
        out.write(value ? 0xc3 : 0xc2);
    }

    /**
     * Writes an integer value using the smallest possible encoding.
     *
     * @param value the integer value
     */
    public void writeInt(int value) {
        writeLong(value);
    }

    /**
     * Writes a long value using the smallest possible encoding.
     *
     * @param value the long value
     */
    public void writeLong(long value) {
        if (value >= 0) {
            if (value <= 0x7f) {
                // positive fixint
                out.write((int) value);
            } else if (value <= 0xff) {
                // uint8
                out.write(0xcc);
                out.write((int) value);
            } else if (value <= 0xffff) {
                // uint16
                out.write(0xcd);
                out.write((int) (value >> 8) & 0xff);
                out.write((int) value & 0xff);
            } else if (value <= 0xffffffffL) {
                // uint32
                out.write(0xce);
                out.write((int) (value >> 24) & 0xff);
                out.write((int) (value >> 16) & 0xff);
                out.write((int) (value >> 8) & 0xff);
                out.write((int) value & 0xff);
            } else {
                // uint64
                out.write(0xcf);
                writeRawLong(value);
            }
        } else {
            if (value >= -32) {
                // negative fixint
                out.write((int) value & 0xff);
            } else if (value >= -128) {
                // int8
                out.write(0xd0);
                out.write((int) value & 0xff);
            } else if (value >= -32768) {
                // int16
                out.write(0xd1);
                out.write((int) (value >> 8) & 0xff);
                out.write((int) value & 0xff);
            } else if (value >= -2147483648L) {
                // int32
                out.write(0xd2);
                out.write((int) (value >> 24) & 0xff);
                out.write((int) (value >> 16) & 0xff);
                out.write((int) (value >> 8) & 0xff);
                out.write((int) value & 0xff);
            } else {
                // int64
                out.write(0xd3);
                writeRawLong(value);
            }
        }
    }

    /**
     * Writes a 32-bit float value.
     *
     * @param value the float value
     */
    public void writeFloat(float value) {
        out.write(0xca);
        int bits = Float.floatToIntBits(value);
        out.write((bits >> 24) & 0xff);
        out.write((bits >> 16) & 0xff);
        out.write((bits >> 8) & 0xff);
        out.write(bits & 0xff);
    }

    /**
     * Writes a 64-bit double value.
     *
     * @param value the double value
     */
    public void writeDouble(double value) {
        out.write(0xcb);
        long bits = Double.doubleToLongBits(value);
        writeRawLong(bits);
    }

    /**
     * Writes a string value.
     *
     * @param value the string value
     */
    public void writeString(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int len = bytes.length;
        if (len <= 31) {
            // fixstr
            out.write(0xa0 | len);
        } else if (len <= 0xff) {
            // str8
            out.write(0xd9);
            out.write(len);
        } else if (len <= 0xffff) {
            // str16
            out.write(0xda);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        } else {
            // str32
            out.write(0xdb);
            out.write((len >> 24) & 0xff);
            out.write((len >> 16) & 0xff);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        }
        out.write(bytes, 0, len);
    }

    /**
     * Writes a binary value.
     *
     * @param value the byte array
     */
    public void writeBinary(byte[] value) {
        int len = value.length;
        if (len <= 0xff) {
            // bin8
            out.write(0xc4);
            out.write(len);
        } else if (len <= 0xffff) {
            // bin16
            out.write(0xc5);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        } else {
            // bin32
            out.write(0xc6);
            out.write((len >> 24) & 0xff);
            out.write((len >> 16) & 0xff);
            out.write((len >> 8) & 0xff);
            out.write(len & 0xff);
        }
        out.write(value, 0, len);
    }

    /**
     * Writes an array header followed by each element.
     *
     * @param list the list of elements to write
     */
    public void writeArray(List<Object> list) {
        int size = list.size();
        if (size <= 15) {
            // fixarray
            out.write(0x90 | size);
        } else if (size <= 0xffff) {
            // array16
            out.write(0xdc);
            out.write((size >> 8) & 0xff);
            out.write(size & 0xff);
        } else {
            // array32
            out.write(0xdd);
            out.write((size >> 24) & 0xff);
            out.write((size >> 16) & 0xff);
            out.write((size >> 8) & 0xff);
            out.write(size & 0xff);
        }
        for (var item : list) {
            writeObject(item);
        }
    }

    /**
     * Writes a map header followed by each key-value pair.
     *
     * @param map the map to write
     */
    public void writeMap(Map<String, Object> map) {
        int size = map.size();
        if (size <= 15) {
            // fixmap
            out.write(0x80 | size);
        } else if (size <= 0xffff) {
            // map16
            out.write(0xde);
            out.write((size >> 8) & 0xff);
            out.write(size & 0xff);
        } else {
            // map32
            out.write(0xdf);
            out.write((size >> 24) & 0xff);
            out.write((size >> 16) & 0xff);
            out.write((size >> 8) & 0xff);
            out.write(size & 0xff);
        }
        for (var entry : map.entrySet()) {
            writeString(entry.getKey());
            writeObject(entry.getValue());
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
