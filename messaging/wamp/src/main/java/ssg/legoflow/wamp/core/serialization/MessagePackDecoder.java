package ssg.legoflow.wamp.core.serialization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes MessagePack binary data to Java objects.
 * Implements the MessagePack specification from scratch without external libraries.
 *
 * <p>Decodes to: {@code null}, {@code Boolean}, {@code Integer}/{@code Long},
 * {@code Float}/{@code Double}, {@code String}, {@code byte[]},
 * {@code List<Object>}, {@code Map<String, Object>}.</p>
 *
 * @see <a href="https://github.com/msgpack/msgpack/blob/master/spec.md">MessagePack spec</a>
 * @since 1.0.0
 */
public class MessagePackDecoder {

    private byte[] data;
    private int pos;

    /**
     * Decodes the given MessagePack bytes into a Java object.
     *
     * @param bytes the MessagePack encoded bytes
     * @return the decoded Java object
     * @throws IllegalArgumentException if the data is malformed
     */
    public Object decode(byte[] bytes) {
        this.data = bytes;
        this.pos = 0;
        return readObject();
    }

    /**
     * Reads the next object from the current position.
     *
     * @return the decoded object
     */
    public Object readObject() {
        if (pos >= data.length) {
            throw new IllegalArgumentException("Unexpected end of MessagePack data");
        }
        int b = data[pos++] & 0xff;

        // positive fixint: 0x00 - 0x7f
        if (b <= 0x7f) {
            return b;
        }
        // fixmap: 0x80 - 0x8f
        if (b >= 0x80 && b <= 0x8f) {
            return readMapEntries(b & 0x0f);
        }
        // fixarray: 0x90 - 0x9f
        if (b >= 0x90 && b <= 0x9f) {
            return readArrayEntries(b & 0x0f);
        }
        // fixstr: 0xa0 - 0xbf
        if (b >= 0xa0 && b <= 0xbf) {
            return readStringBytes(b & 0x1f);
        }
        // negative fixint: 0xe0 - 0xff
        if (b >= 0xe0) {
            return (int) (byte) b;
        }

        return switch (b) {
            case 0xc0 -> null;  // nil
            case 0xc2 -> false; // false
            case 0xc3 -> true;  // true

            // bin8, bin16, bin32
            case 0xc4 -> readBinaryBytes(readUint8());
            case 0xc5 -> readBinaryBytes(readUint16());
            case 0xc6 -> readBinaryBytes(readUint32AsInt());

            // float32, float64
            case 0xca -> Float.intBitsToFloat(readInt32());
            case 0xcb -> Double.longBitsToDouble(readInt64());

            // uint8, uint16, uint32, uint64
            case 0xcc -> readUint8();
            case 0xcd -> readUint16();
            case 0xce -> readUint32();
            case 0xcf -> readUint64();

            // int8, int16, int32, int64
            case 0xd0 -> (int) (byte) (data[pos++] & 0xff);
            case 0xd1 -> (int) (short) readRawInt16();
            case 0xd2 -> readInt32();
            case 0xd3 -> readInt64();

            // str8, str16, str32
            case 0xd9 -> readStringBytes(readUint8());
            case 0xda -> readStringBytes(readUint16());
            case 0xdb -> readStringBytes(readUint32AsInt());

            // array16, array32
            case 0xdc -> readArrayEntries(readUint16());
            case 0xdd -> readArrayEntries(readUint32AsInt());

            // map16, map32
            case 0xde -> readMapEntries(readUint16());
            case 0xdf -> readMapEntries(readUint32AsInt());

            default -> throw new IllegalArgumentException(
                    "Unsupported MessagePack format byte: 0x" + Integer.toHexString(b));
        };
    }

    private int readUint8() {
        return data[pos++] & 0xff;
    }

    private int readUint16() {
        int hi = data[pos++] & 0xff;
        int lo = data[pos++] & 0xff;
        return (hi << 8) | lo;
    }

    private int readRawInt16() {
        int hi = data[pos++] & 0xff;
        int lo = data[pos++] & 0xff;
        return (hi << 8) | lo;
    }

    private Object readUint32() {
        long val = readUint32Long();
        if (val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private long readUint32Long() {
        long b0 = data[pos++] & 0xff;
        long b1 = data[pos++] & 0xff;
        long b2 = data[pos++] & 0xff;
        long b3 = data[pos++] & 0xff;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private int readUint32AsInt() {
        return (int) readUint32Long();
    }

    private int readInt32() {
        int b0 = data[pos++] & 0xff;
        int b1 = data[pos++] & 0xff;
        int b2 = data[pos++] & 0xff;
        int b3 = data[pos++] & 0xff;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private Object readUint64() {
        long val = readInt64();
        // If high bit is set, value exceeds long range — return as long anyway
        // (Java doesn't have unsigned long; this is the best we can do)
        if (val >= 0) return val;
        return val; // still return as long
    }

    private long readInt64() {
        long val = 0;
        for (int i = 0; i < 8; i++) {
            val = (val << 8) | (data[pos++] & 0xff);
        }
        return val;
    }

    private String readStringBytes(int len) {
        var str = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return str;
    }

    private byte[] readBinaryBytes(int len) {
        var bytes = new byte[len];
        System.arraycopy(data, pos, bytes, 0, len);
        pos += len;
        return bytes;
    }

    private List<Object> readArrayEntries(int count) {
        var list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(readObject());
        }
        return list;
    }

    private Map<String, Object> readMapEntries(int count) {
        var map = new HashMap<String, Object>(count);
        for (int i = 0; i < count; i++) {
            var key = readObject();
            var value = readObject();
            map.put(key.toString(), value);
        }
        return map;
    }
}
