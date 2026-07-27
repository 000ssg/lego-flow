package ssg.legoflow.messaging.amqp.types;

import ssg.legoflow.messaging.amqp.common.AmqpError;
import ssg.legoflow.messaging.amqp.common.AmqpException;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Encodes and decodes AMQP 1.0 type system values to/from binary wire format.
 *
 * <p>Implements the self-describing encoding scheme where each value is preceded
 * by a constructor byte (type code). Supports all primitive types, composite types
 * (list, map, array), and described types.
 *
 * <p>Encoding uses the most compact representation available. For example,
 * a uint value of 0 is encoded as {@code 0x43} (uint0) rather than the 4-byte form.
 *
 * @since 1.0.0
 */
public final class TypeCodec {

    private TypeCodec() {}

    // ---- Type codes ----
    // Described type constructor
    public static final byte DESCRIBED = 0x00;

    // Fixed-width primitives
    public static final byte NULL = 0x40;
    public static final byte BOOLEAN_TRUE = 0x41;
    public static final byte BOOLEAN_FALSE = 0x42;
    public static final byte BOOLEAN = 0x56;
    public static final byte UBYTE = 0x50;
    public static final byte USHORT = 0x60;
    public static final byte UINT = 0x70;
    public static final byte UINT_SMALL = 0x52;
    public static final byte UINT_ZERO = 0x43;
    public static final byte ULONG = (byte) 0x80;
    public static final byte ULONG_SMALL = 0x53;
    public static final byte ULONG_ZERO = 0x44;
    public static final byte BYTE = 0x51;
    public static final byte SHORT = 0x61;
    public static final byte INT = 0x71;
    public static final byte INT_SMALL = 0x54;
    public static final byte LONG = (byte) 0x81;
    public static final byte LONG_SMALL = 0x55;
    public static final byte FLOAT = 0x72;
    public static final byte DOUBLE = (byte) 0x82;
    public static final byte CHAR = 0x73;
    public static final byte TIMESTAMP = (byte) 0x83;
    public static final byte UUID = (byte) 0x98;

    // Variable-width primitives
    public static final byte BINARY_SMALL = (byte) 0xa0;
    public static final byte BINARY_LARGE = (byte) 0xb0;
    public static final byte STRING_SMALL = (byte) 0xa1;
    public static final byte STRING_LARGE = (byte) 0xb1;
    public static final byte SYMBOL_SMALL = (byte) 0xa3;
    public static final byte SYMBOL_LARGE = (byte) 0xb3;

    // Composite types
    public static final byte LIST_ZERO = 0x45;
    public static final byte LIST_SMALL = (byte) 0xc0;
    public static final byte LIST_LARGE = (byte) 0xd0;
    public static final byte MAP_SMALL = (byte) 0xc1;
    public static final byte MAP_LARGE = (byte) 0xd1;
    public static final byte ARRAY_SMALL = (byte) 0xe0;
    public static final byte ARRAY_LARGE = (byte) 0xf0;

    // ---- Encoding ----

    /**
     * Encodes an AMQP type value into a ByteBuffer.
     *
     * @param value the value to encode
     * @return a ByteBuffer positioned at 0, with limit set to the encoded size
     */
    public static ByteBuffer encode(AmqpType value) {
        var buf = ByteBuffer.allocate(estimateSize(value));
        encodeInto(value, buf);
        buf.flip();
        return buf;
    }

    /**
     * Encodes an AMQP type value into the given ByteBuffer at its current position.
     *
     * @param value the value to encode
     * @param buf   the target buffer
     */
    public static void encodeInto(AmqpType value, ByteBuffer buf) {
        switch (value) {
            case AmqpType.Null _ -> buf.put(NULL);

            case AmqpType.Bool b -> {
                if (b.value()) {
                    buf.put(BOOLEAN_TRUE);
                } else {
                    buf.put(BOOLEAN_FALSE);
                }
            }

            case AmqpType.UByte ub -> {
                buf.put(UBYTE);
                buf.put((byte) ub.value());
            }

            case AmqpType.UShort us -> {
                buf.put(USHORT);
                buf.putShort((short) us.value());
            }

            case AmqpType.UInt ui -> {
                long v = ui.value();
                if (v == 0) {
                    buf.put(UINT_ZERO);
                } else if (v <= 255) {
                    buf.put(UINT_SMALL);
                    buf.put((byte) v);
                } else {
                    buf.put(UINT);
                    buf.putInt((int) v);
                }
            }

            case AmqpType.ULong ul -> {
                long v = ul.value();
                if (v == 0) {
                    buf.put(ULONG_ZERO);
                } else if (v > 0 && v <= 255) {
                    buf.put(ULONG_SMALL);
                    buf.put((byte) v);
                } else {
                    buf.put(ULONG);
                    buf.putLong(v);
                }
            }

            case AmqpType.Byte b -> {
                buf.put(BYTE);
                buf.put(b.value());
            }

            case AmqpType.Short s -> {
                buf.put(SHORT);
                buf.putShort(s.value());
            }

            case AmqpType.Int i -> {
                int v = i.value();
                if (v >= -128 && v <= 127) {
                    buf.put(INT_SMALL);
                    buf.put((byte) v);
                } else {
                    buf.put(INT);
                    buf.putInt(v);
                }
            }

            case AmqpType.Long l -> {
                long v = l.value();
                if (v >= -128 && v <= 127) {
                    buf.put(LONG_SMALL);
                    buf.put((byte) v);
                } else {
                    buf.put(LONG);
                    buf.putLong(v);
                }
            }

            case AmqpType.Float f -> {
                buf.put(FLOAT);
                buf.putFloat(f.value());
            }

            case AmqpType.Double d -> {
                buf.put(DOUBLE);
                buf.putDouble(d.value());
            }

            case AmqpType.Char c -> {
                buf.put(CHAR);
                buf.putInt(c.codePoint());
            }

            case AmqpType.Timestamp ts -> {
                buf.put(TIMESTAMP);
                buf.putLong(ts.millis());
            }

            case AmqpType.Uuid uuid -> {
                buf.put(UUID);
                buf.putLong(uuid.value().getMostSignificantBits());
                buf.putLong(uuid.value().getLeastSignificantBits());
            }

            case AmqpType.Binary bin -> {
                byte[] data = bin.value();
                if (data.length <= 255) {
                    buf.put(BINARY_SMALL);
                    buf.put((byte) data.length);
                } else {
                    buf.put(BINARY_LARGE);
                    buf.putInt(data.length);
                }
                buf.put(data);
            }

            case AmqpType.AmqpString str -> {
                byte[] data = str.value().getBytes(StandardCharsets.UTF_8);
                if (data.length <= 255) {
                    buf.put(STRING_SMALL);
                    buf.put((byte) data.length);
                } else {
                    buf.put(STRING_LARGE);
                    buf.putInt(data.length);
                }
                buf.put(data);
            }

            case AmqpType.Symbol sym -> {
                byte[] data = sym.value().getBytes(StandardCharsets.US_ASCII);
                if (data.length <= 255) {
                    buf.put(SYMBOL_SMALL);
                    buf.put((byte) data.length);
                } else {
                    buf.put(SYMBOL_LARGE);
                    buf.putInt(data.length);
                }
                buf.put(data);
            }

            case AmqpType.AmqpList list -> encodeList(list, buf);
            case AmqpType.AmqpMap map -> encodeMap(map, buf);
            case AmqpType.AmqpArray arr -> encodeArray(arr, buf);
            case AmqpType.Described desc -> encodeDescribed(desc, buf);
        }
    }

    private static void encodeList(AmqpType.AmqpList list, ByteBuffer buf) {
        var elements = list.elements();
        if (elements.isEmpty()) {
            buf.put(LIST_ZERO);
            return;
        }
        // Encode elements into a temporary buffer to measure size
        var tmp = ByteBuffer.allocate(estimateListBodySize(elements));
        for (var elem : elements) {
            encodeInto(elem, tmp);
        }
        tmp.flip();
        int count = elements.size();
        int bodySize = tmp.remaining();
        if (bodySize + sizeOfCount(count) <= 255) {
            buf.put(LIST_SMALL);
            buf.put((byte) (bodySize + 1)); // size includes count byte
            buf.put((byte) count);
        } else {
            buf.put(LIST_LARGE);
            buf.putInt(bodySize + 4); // size includes count int
            buf.putInt(count);
        }
        buf.put(tmp);
    }

    private static void encodeMap(AmqpType.AmqpMap map, ByteBuffer buf) {
        var entries = map.entries();
        if (entries.isEmpty()) {
            buf.put(MAP_SMALL);
            buf.put((byte) 1); // size = 1 (count byte)
            buf.put((byte) 0); // count = 0
            return;
        }
        // Encode all key-value pairs
        var tmp = ByteBuffer.allocate(estimateMapBodySize(entries));
        for (var entry : entries.entrySet()) {
            encodeInto(entry.getKey(), tmp);
            encodeInto(entry.getValue(), tmp);
        }
        tmp.flip();
        int count = entries.size() * 2; // AMQP map count is key+value pairs
        int bodySize = tmp.remaining();
        if (bodySize + sizeOfCount(count) <= 255) {
            buf.put(MAP_SMALL);
            buf.put((byte) (bodySize + 1));
            buf.put((byte) count);
        } else {
            buf.put(MAP_LARGE);
            buf.putInt(bodySize + 4);
            buf.putInt(count);
        }
        buf.put(tmp);
    }

    private static void encodeArray(AmqpType.AmqpArray arr, ByteBuffer buf) {
        var elements = arr.elements();
        if (elements.isEmpty()) {
            buf.put(ARRAY_SMALL);
            buf.put((byte) 1); // size = 1 (count byte)
            buf.put((byte) 0); // count = 0
            return;
        }
        // Encode elements without their own constructors (array elements share a constructor)
        // For simplicity, we encode elements with constructors and include the shared constructor
        var tmp = ByteBuffer.allocate(estimateListBodySize(elements));
        // Write the shared constructor byte (type of first element)
        byte sharedConstructor = constructorByteFor(elements.getFirst());
        tmp.put(sharedConstructor);
        // Write element values (without constructor bytes)
        for (var elem : elements) {
            encodeValueOnly(elem, tmp);
        }
        tmp.flip();
        int count = elements.size();
        int bodySize = tmp.remaining();
        if (bodySize + sizeOfCount(count) <= 255) {
            buf.put(ARRAY_SMALL);
            buf.put((byte) (bodySize + 1));
            buf.put((byte) count);
        } else {
            buf.put(ARRAY_LARGE);
            buf.putInt(bodySize + 4);
            buf.putInt(count);
        }
        buf.put(tmp);
    }

    private static void encodeDescribed(AmqpType.Described desc, ByteBuffer buf) {
        buf.put(DESCRIBED);
        encodeInto(desc.descriptor(), buf);
        encodeInto(desc.described(), buf);
    }

    /**
     * Encodes only the value portion of a type (no constructor byte).
     * Used for array element encoding where elements share a single constructor.
     */
    private static void encodeValueOnly(AmqpType value, ByteBuffer buf) {
        switch (value) {
            case AmqpType.Null _ -> {} // no value bytes
            case AmqpType.Bool b -> buf.put(b.value() ? (byte) 1 : (byte) 0);
            case AmqpType.UByte ub -> buf.put((byte) ub.value());
            case AmqpType.UShort us -> buf.putShort((short) us.value());
            case AmqpType.UInt ui -> buf.putInt((int) ui.value());
            case AmqpType.ULong ul -> buf.putLong(ul.value());
            case AmqpType.Byte b -> buf.put(b.value());
            case AmqpType.Short s -> buf.putShort(s.value());
            case AmqpType.Int i -> buf.putInt(i.value());
            case AmqpType.Long l -> buf.putLong(l.value());
            case AmqpType.Float f -> buf.putFloat(f.value());
            case AmqpType.Double d -> buf.putDouble(d.value());
            case AmqpType.Char c -> buf.putInt(c.codePoint());
            case AmqpType.Timestamp ts -> buf.putLong(ts.millis());
            case AmqpType.Uuid uuid -> {
                buf.putLong(uuid.value().getMostSignificantBits());
                buf.putLong(uuid.value().getLeastSignificantBits());
            }
            case AmqpType.Binary bin -> {
                byte[] data = bin.value();
                if (data.length <= 255) {
                    buf.put((byte) data.length);
                    buf.put(data);
                } else {
                    buf.putInt(data.length);
                    buf.put(data);
                }
            }
            case AmqpType.AmqpString str -> {
                byte[] data = str.value().getBytes(StandardCharsets.UTF_8);
                if (data.length <= 255) {
                    buf.put((byte) data.length);
                    buf.put(data);
                } else {
                    buf.putInt(data.length);
                    buf.put(data);
                }
            }
            case AmqpType.Symbol sym -> {
                byte[] data = sym.value().getBytes(StandardCharsets.US_ASCII);
                if (data.length <= 255) {
                    buf.put((byte) data.length);
                    buf.put(data);
                } else {
                    buf.putInt(data.length);
                    buf.put(data);
                }
            }
            case AmqpType.AmqpList list -> encodeList(list, buf);
            case AmqpType.AmqpMap map -> encodeMap(map, buf);
            case AmqpType.AmqpArray arr -> encodeArray(arr, buf);
            case AmqpType.Described desc -> encodeDescribed(desc, buf);
        }
    }

    /**
     * Returns the constructor byte for a given AmqpType value.
     * Used to determine the shared constructor for array encoding.
     */
    static byte constructorByteFor(AmqpType value) {
        return switch (value) {
            case AmqpType.Null _ -> NULL;
            case AmqpType.Bool _ -> BOOLEAN;
            case AmqpType.UByte _ -> UBYTE;
            case AmqpType.UShort _ -> USHORT;
            case AmqpType.UInt _ -> UINT;
            case AmqpType.ULong _ -> ULONG;
            case AmqpType.Byte _ -> BYTE;
            case AmqpType.Short _ -> SHORT;
            case AmqpType.Int _ -> INT;
            case AmqpType.Long _ -> LONG;
            case AmqpType.Float _ -> FLOAT;
            case AmqpType.Double _ -> DOUBLE;
            case AmqpType.Char _ -> CHAR;
            case AmqpType.Timestamp _ -> TIMESTAMP;
            case AmqpType.Uuid _ -> UUID;
            case AmqpType.Binary bin -> bin.value().length <= 255 ? BINARY_SMALL : BINARY_LARGE;
            case AmqpType.AmqpString str ->
                    str.value().getBytes(StandardCharsets.UTF_8).length <= 255 ? STRING_SMALL : STRING_LARGE;
            case AmqpType.Symbol sym ->
                    sym.value().getBytes(StandardCharsets.US_ASCII).length <= 255 ? SYMBOL_SMALL : SYMBOL_LARGE;
            case AmqpType.AmqpList list -> list.elements().isEmpty() ? LIST_ZERO : LIST_SMALL;
            case AmqpType.AmqpMap _ -> MAP_SMALL;
            case AmqpType.AmqpArray _ -> ARRAY_SMALL;
            case AmqpType.Described _ -> DESCRIBED;
        };
    }

    // ---- Decoding ----

    /**
     * Decodes an AMQP type value from a ByteBuffer at its current position.
     *
     * @param buf the source buffer
     * @return the decoded value
     * @throws AmqpException if the buffer contains invalid encoding
     */
    public static AmqpType decode(ByteBuffer buf) {
        if (!buf.hasRemaining()) {
            throw new AmqpException(AmqpError.DECODE_ERROR, "Buffer underflow during decode");
        }
        byte code = buf.get();
        return decodeForCode(code, buf);
    }

    private static AmqpType decodeForCode(byte code, ByteBuffer buf) {
        return switch (code) {
            case DESCRIBED -> {
                AmqpType descriptor = decode(buf);
                AmqpType described = decode(buf);
                yield new AmqpType.Described(descriptor, described);
            }
            case NULL -> new AmqpType.Null();
            case BOOLEAN_TRUE -> new AmqpType.Bool(true);
            case BOOLEAN_FALSE -> new AmqpType.Bool(false);
            case BOOLEAN -> new AmqpType.Bool(buf.get() != 0);
            case UBYTE -> new AmqpType.UByte((short) (buf.get() & 0xFF));
            case USHORT -> new AmqpType.UShort(buf.getShort() & 0xFFFF);
            case UINT -> new AmqpType.UInt(buf.getInt() & 0xFFFFFFFFL);
            case UINT_SMALL -> new AmqpType.UInt(buf.get() & 0xFFL);
            case UINT_ZERO -> new AmqpType.UInt(0);
            case ULONG -> new AmqpType.ULong(buf.getLong());
            case ULONG_SMALL -> new AmqpType.ULong(buf.get() & 0xFFL);
            case ULONG_ZERO -> new AmqpType.ULong(0);
            case BYTE -> new AmqpType.Byte(buf.get());
            case SHORT -> new AmqpType.Short(buf.getShort());
            case INT -> new AmqpType.Int(buf.getInt());
            case INT_SMALL -> new AmqpType.Int(buf.get());
            case LONG -> new AmqpType.Long(buf.getLong());
            case LONG_SMALL -> new AmqpType.Long(buf.get());
            case FLOAT -> new AmqpType.Float(buf.getFloat());
            case DOUBLE -> new AmqpType.Double(buf.getDouble());
            case CHAR -> new AmqpType.Char(buf.getInt());
            case TIMESTAMP -> new AmqpType.Timestamp(buf.getLong());
            case UUID -> {
                long msb = buf.getLong();
                long lsb = buf.getLong();
                yield new AmqpType.Uuid(new java.util.UUID(msb, lsb));
            }
            case BINARY_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Binary(data);
            }
            case BINARY_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Binary(data);
            }
            case STRING_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.AmqpString(new String(data, StandardCharsets.UTF_8));
            }
            case STRING_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.AmqpString(new String(data, StandardCharsets.UTF_8));
            }
            case SYMBOL_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Symbol(new String(data, StandardCharsets.US_ASCII));
            }
            case SYMBOL_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Symbol(new String(data, StandardCharsets.US_ASCII));
            }
            case LIST_ZERO -> new AmqpType.AmqpList(List.of());
            case LIST_SMALL -> decodeListSmall(buf);
            case LIST_LARGE -> decodeListLarge(buf);
            case MAP_SMALL -> decodeMapSmall(buf);
            case MAP_LARGE -> decodeMapLarge(buf);
            case ARRAY_SMALL -> decodeArraySmall(buf);
            case ARRAY_LARGE -> decodeArrayLarge(buf);
            default -> throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Unknown type code: 0x" + String.format("%02x", code & 0xFF));
        };
    }

    private static AmqpType.AmqpList decodeListSmall(ByteBuffer buf) {
        int size = buf.get() & 0xFF;
        int count = buf.get() & 0xFF;
        if (count == 0) return new AmqpType.AmqpList(List.of());
        int endPos = buf.position() + size - 1; // -1 for count byte already read
        var elements = new ArrayList<AmqpType>(count);
        for (int i = 0; i < count; i++) {
            elements.add(decode(buf));
        }
        return new AmqpType.AmqpList(elements);
    }

    private static AmqpType.AmqpList decodeListLarge(ByteBuffer buf) {
        int size = buf.getInt();
        int count = buf.getInt();
        if (count == 0) return new AmqpType.AmqpList(List.of());
        var elements = new ArrayList<AmqpType>(count);
        for (int i = 0; i < count; i++) {
            elements.add(decode(buf));
        }
        return new AmqpType.AmqpList(elements);
    }

    private static AmqpType.AmqpMap decodeMapSmall(ByteBuffer buf) {
        int size = buf.get() & 0xFF;
        int count = buf.get() & 0xFF;
        if (count == 0) return new AmqpType.AmqpMap(Map.of());
        var entries = new LinkedHashMap<AmqpType, AmqpType>(count / 2);
        for (int i = 0; i < count; i += 2) {
            AmqpType key = decode(buf);
            AmqpType value = decode(buf);
            entries.put(key, value);
        }
        return new AmqpType.AmqpMap(entries);
    }

    private static AmqpType.AmqpMap decodeMapLarge(ByteBuffer buf) {
        int size = buf.getInt();
        int count = buf.getInt();
        if (count == 0) return new AmqpType.AmqpMap(Map.of());
        var entries = new LinkedHashMap<AmqpType, AmqpType>(count / 2);
        for (int i = 0; i < count; i += 2) {
            AmqpType key = decode(buf);
            AmqpType value = decode(buf);
            entries.put(key, value);
        }
        return new AmqpType.AmqpMap(entries);
    }

    private static AmqpType.AmqpArray decodeArraySmall(ByteBuffer buf) {
        int size = buf.get() & 0xFF;
        int count = buf.get() & 0xFF;
        if (count == 0) return new AmqpType.AmqpArray(List.of());
        byte constructor = buf.get();
        var elements = new ArrayList<AmqpType>(count);
        for (int i = 0; i < count; i++) {
            elements.add(decodeArrayElement(constructor, buf));
        }
        return new AmqpType.AmqpArray(elements);
    }

    private static AmqpType.AmqpArray decodeArrayLarge(ByteBuffer buf) {
        int size = buf.getInt();
        int count = buf.getInt();
        if (count == 0) return new AmqpType.AmqpArray(List.of());
        byte constructor = buf.get();
        var elements = new ArrayList<AmqpType>(count);
        for (int i = 0; i < count; i++) {
            elements.add(decodeArrayElement(constructor, buf));
        }
        return new AmqpType.AmqpArray(elements);
    }

    /**
     * Decodes an array element using the shared constructor byte.
     * Array elements don't have their own constructor byte.
     */
    private static AmqpType decodeArrayElement(byte constructor, ByteBuffer buf) {
        return switch (constructor) {
            case NULL -> new AmqpType.Null();
            case BOOLEAN -> new AmqpType.Bool(buf.get() != 0);
            case UBYTE -> new AmqpType.UByte((short) (buf.get() & 0xFF));
            case USHORT -> new AmqpType.UShort(buf.getShort() & 0xFFFF);
            case UINT -> new AmqpType.UInt(buf.getInt() & 0xFFFFFFFFL);
            case ULONG -> new AmqpType.ULong(buf.getLong());
            case BYTE -> new AmqpType.Byte(buf.get());
            case SHORT -> new AmqpType.Short(buf.getShort());
            case INT -> new AmqpType.Int(buf.getInt());
            case LONG -> new AmqpType.Long(buf.getLong());
            case FLOAT -> new AmqpType.Float(buf.getFloat());
            case DOUBLE -> new AmqpType.Double(buf.getDouble());
            case CHAR -> new AmqpType.Char(buf.getInt());
            case TIMESTAMP -> new AmqpType.Timestamp(buf.getLong());
            case UUID -> {
                long msb = buf.getLong();
                long lsb = buf.getLong();
                yield new AmqpType.Uuid(new java.util.UUID(msb, lsb));
            }
            case BINARY_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Binary(data);
            }
            case BINARY_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Binary(data);
            }
            case STRING_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.AmqpString(new String(data, StandardCharsets.UTF_8));
            }
            case STRING_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.AmqpString(new String(data, StandardCharsets.UTF_8));
            }
            case SYMBOL_SMALL -> {
                int len = buf.get() & 0xFF;
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Symbol(new String(data, StandardCharsets.US_ASCII));
            }
            case SYMBOL_LARGE -> {
                int len = buf.getInt();
                byte[] data = new byte[len];
                buf.get(data);
                yield new AmqpType.Symbol(new String(data, StandardCharsets.US_ASCII));
            }
            default -> throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Unsupported array element constructor: 0x" + String.format("%02x", constructor & 0xFF));
        };
    }

    // ---- Size estimation ----

    /**
     * Estimates the encoded size in bytes for buffer allocation.
     * Intentionally over-estimates to avoid buffer overflow.
     *
     * @param value the value to estimate
     * @return estimated byte count
     */
    public static int estimateSize(AmqpType value) {
        return switch (value) {
            case AmqpType.Null _ -> 1;
            case AmqpType.Bool _ -> 1;
            case AmqpType.UByte _ -> 2;
            case AmqpType.UShort _ -> 3;
            case AmqpType.UInt _ -> 5;
            case AmqpType.ULong _ -> 9;
            case AmqpType.Byte _ -> 2;
            case AmqpType.Short _ -> 3;
            case AmqpType.Int _ -> 5;
            case AmqpType.Long _ -> 9;
            case AmqpType.Float _ -> 5;
            case AmqpType.Double _ -> 9;
            case AmqpType.Char _ -> 5;
            case AmqpType.Timestamp _ -> 9;
            case AmqpType.Uuid _ -> 17;
            case AmqpType.Binary bin -> 5 + bin.value().length;
            case AmqpType.AmqpString str -> 5 + str.value().getBytes(StandardCharsets.UTF_8).length;
            case AmqpType.Symbol sym -> 5 + sym.value().getBytes(StandardCharsets.US_ASCII).length;
            case AmqpType.AmqpList list -> 9 + estimateListBodySize(list.elements());
            case AmqpType.AmqpMap map -> 9 + estimateMapBodySize(map.entries());
            case AmqpType.AmqpArray arr -> 10 + estimateListBodySize(arr.elements());
            case AmqpType.Described desc -> 1 + estimateSize(desc.descriptor()) + estimateSize(desc.described());
        };
    }

    private static int estimateListBodySize(List<AmqpType> elements) {
        int total = 0;
        for (var elem : elements) {
            total += estimateSize(elem);
        }
        return total;
    }

    private static int estimateMapBodySize(Map<AmqpType, AmqpType> entries) {
        int total = 0;
        for (var entry : entries.entrySet()) {
            total += estimateSize(entry.getKey()) + estimateSize(entry.getValue());
        }
        return total;
    }

    private static int sizeOfCount(int count) {
        return count <= 255 ? 1 : 4;
    }

    // ---- Utility methods ----

    /**
     * Extracts a Java string from an AmqpType, accepting both String and Symbol types.
     *
     * @param type the AMQP type value
     * @return the string value
     * @throws AmqpException if the type is not a string or symbol
     */
    public static String toString(AmqpType type) {
        return switch (type) {
            case AmqpType.AmqpString s -> s.value();
            case AmqpType.Symbol s -> s.value();
            default -> throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Expected string or symbol, got: " + type.getClass().getSimpleName());
        };
    }

    /**
     * Extracts a Java long from an AmqpType, accepting integer types.
     *
     * @param type the AMQP type value
     * @return the long value
     * @throws AmqpException if the type is not numeric
     */
    public static long toLong(AmqpType type) {
        return switch (type) {
            case AmqpType.UByte ub -> ub.value();
            case AmqpType.UShort us -> us.value();
            case AmqpType.UInt ui -> ui.value();
            case AmqpType.ULong ul -> ul.value();
            case AmqpType.Byte b -> b.value();
            case AmqpType.Short s -> s.value();
            case AmqpType.Int i -> i.value();
            case AmqpType.Long l -> l.value();
            default -> throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Expected numeric type, got: " + type.getClass().getSimpleName());
        };
    }

    /**
     * Extracts a boolean from an AmqpType.
     *
     * @param type the AMQP type value
     * @return the boolean value
     * @throws AmqpException if the type is not a boolean
     */
    public static boolean toBoolean(AmqpType type) {
        return switch (type) {
            case AmqpType.Bool b -> b.value();
            default -> throw new AmqpException(AmqpError.DECODE_ERROR,
                    "Expected boolean, got: " + type.getClass().getSimpleName());
        };
    }

    /**
     * Gets a field from a described list by index, returning null if the index is
     * out of bounds or the element is null.
     *
     * @param list  the AMQP list
     * @param index the field index
     * @return the field value or null
     */
    public static AmqpType getField(AmqpType.AmqpList list, int index) {
        if (index >= list.elements().size()) return null;
        AmqpType val = list.elements().get(index);
        return val instanceof AmqpType.Null ? null : val;
    }
}
