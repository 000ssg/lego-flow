package ssg.legoflow.wamp.core.serialization;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Decodes CBOR (Concise Binary Object Representation) binary data to Java objects.
 * Implements RFC 8949 from scratch without external libraries.
 *
 * <p>Decodes to: {@code null}, {@code Boolean}, {@code Integer}/{@code Long},
 * {@code Float}/{@code Double}, {@code String}, {@code byte[]},
 * {@code List<Object>}, {@code Map<String, Object>}.</p>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc8949">RFC 8949 — CBOR</a>
 * @since 1.0.0
 */
public class CborDecoder {

    private byte[] data;
    private int pos;

    /**
     * Decodes the given CBOR bytes into a Java object.
     *
     * @param bytes the CBOR encoded bytes
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
            throw new IllegalArgumentException("Unexpected end of CBOR data");
        }
        int ib = data[pos++] & 0xff;
        int majorType = ib >> 5;
        int additionalInfo = ib & 0x1f;

        return switch (majorType) {
            case 0 -> readUnsignedInt(additionalInfo);
            case 1 -> readNegativeInt(additionalInfo);
            case 2 -> readByteString(additionalInfo);
            case 3 -> readTextString(additionalInfo);
            case 4 -> readArray(additionalInfo);
            case 5 -> readMap(additionalInfo);
            case 7 -> readSimpleOrFloat(additionalInfo);
            default -> throw new IllegalArgumentException("Unsupported CBOR major type: " + majorType);
        };
    }

    private Object readUnsignedInt(int ai) {
        long val = readArgument(ai);
        if (val >= 0 && val <= Integer.MAX_VALUE) return (int) val;
        return val;
    }

    private Object readNegativeInt(int ai) {
        long val = readArgument(ai);
        long result = -1L - val;
        if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) return (int) result;
        return result;
    }

    private byte[] readByteString(int ai) {
        int len = (int) readArgument(ai);
        var bytes = new byte[len];
        System.arraycopy(data, pos, bytes, 0, len);
        pos += len;
        return bytes;
    }

    private String readTextString(int ai) {
        int len = (int) readArgument(ai);
        var str = new String(data, pos, len, StandardCharsets.UTF_8);
        pos += len;
        return str;
    }

    private List<Object> readArray(int ai) {
        int count = (int) readArgument(ai);
        var list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(readObject());
        }
        return list;
    }

    private Map<String, Object> readMap(int ai) {
        int count = (int) readArgument(ai);
        var map = new HashMap<String, Object>(count);
        for (int i = 0; i < count; i++) {
            var key = readObject();
            var value = readObject();
            map.put(key.toString(), value);
        }
        return map;
    }

    private Object readSimpleOrFloat(int ai) {
        return switch (ai) {
            case 20 -> false;
            case 21 -> true;
            case 22 -> null; // null
            case 23 -> null; // undefined -> null
            case 25 -> readFloat16();
            case 26 -> readFloat32();
            case 27 -> readFloat64();
            default -> throw new IllegalArgumentException("Unsupported CBOR simple value: " + ai);
        };
    }

    private float readFloat16() {
        // Half-precision float (IEEE 754)
        int half = readRawUint16();
        int sign = (half >> 15) & 1;
        int exp = (half >> 10) & 0x1f;
        int mant = half & 0x3ff;
        float result;
        if (exp == 0) {
            result = (float) (Math.pow(2, -14) * (mant / 1024.0));
        } else if (exp == 31) {
            result = mant == 0 ? Float.POSITIVE_INFINITY : Float.NaN;
        } else {
            result = (float) (Math.pow(2, exp - 15) * (1 + mant / 1024.0));
        }
        return sign == 1 ? -result : result;
    }

    private float readFloat32() {
        return Float.intBitsToFloat(readRawInt32());
    }

    private double readFloat64() {
        return Double.longBitsToDouble(readRawInt64());
    }

    private long readArgument(int ai) {
        if (ai <= 23) return ai;
        return switch (ai) {
            case 24 -> data[pos++] & 0xff;
            case 25 -> (long) readRawUint16();
            case 26 -> readRawUint32();
            case 27 -> readRawInt64();
            default -> throw new IllegalArgumentException("Invalid CBOR additional info: " + ai);
        };
    }

    private int readRawUint16() {
        int hi = data[pos++] & 0xff;
        int lo = data[pos++] & 0xff;
        return (hi << 8) | lo;
    }

    private long readRawUint32() {
        long b0 = data[pos++] & 0xff;
        long b1 = data[pos++] & 0xff;
        long b2 = data[pos++] & 0xff;
        long b3 = data[pos++] & 0xff;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private int readRawInt32() {
        int b0 = data[pos++] & 0xff;
        int b1 = data[pos++] & 0xff;
        int b2 = data[pos++] & 0xff;
        int b3 = data[pos++] & 0xff;
        return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
    }

    private long readRawInt64() {
        long val = 0;
        for (int i = 0; i < 8; i++) {
            val = (val << 8) | (data[pos++] & 0xff);
        }
        return val;
    }
}
