package ssg.legoflow.rpc.grpc.protobuf;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
/**
 * Encodes and decodes protobuf binary format.
 * Supports varints, zigzag encoding, all wire types, nested messages,
 * repeated fields, packed repeated fields, maps, and oneofs.
 */
public final class ProtobufCodec {

    private ProtobufCodec() {
    }

    // ---- Varint encoding/decoding (LEB128) ----

    public static byte[] encodeVarint(long value) {
        var out = new ByteArrayOutputStream(10);
        while ((value & ~0x7FL) != 0) {
            out.write((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.write((int) (value & 0x7F));
        return out.toByteArray();
    }

    public static long decodeVarint(ByteBuffer buf) {
        long result = 0;
        int shift = 0;
        while (buf.hasRemaining()) {
            byte b = buf.get();
            result |= (long) (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return result;
            }
            shift += 7;
            if (shift >= 64) {
                throw new IllegalArgumentException("Varint too long");
            }
        }
        throw new IllegalArgumentException("Unexpected end of buffer while reading varint");
    }

    // ---- ZigZag encoding/decoding ----

    public static long zigzagEncode(long value) {
        return (value << 1) ^ (value >> 63);
    }

    public static int zigzagEncode(int value) {
        return (value << 1) ^ (value >> 31);
    }

    public static long zigzagDecode(long encoded) {
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    public static int zigzagDecode(int encoded) {
        return (encoded >>> 1) ^ -(encoded & 1);
    }

    // ---- Encode a ProtoMessage ----

    /**
     * Encodes a ProtoMessage to protobuf binary format using the given descriptor.
     */
    public static byte[] encode(ProtoMessage message, MessageDescriptor descriptor) {
        var out = new ByteArrayOutputStream();
        for (var field : descriptor.fields()) {
            var value = message.get(field.number());
            if (value == null) continue;
            encodeField(out, field, value);
        }
        return out.toByteArray();
    }

    /**
     * Encodes a ProtoMessage to protobuf binary format without a descriptor (schema-less).
     * Uses the wire types inferred from the FieldValue types.
     */
    public static byte[] encode(ProtoMessage message) {
        var out = new ByteArrayOutputStream();
        for (var entry : message.toMap().entrySet()) {
            int fieldNumber = entry.getKey();
            var value = entry.getValue();
            encodeFieldSchemaless(out, fieldNumber, value);
        }
        return out.toByteArray();
    }

    private static void encodeField(ByteArrayOutputStream out, FieldDescriptor field, FieldValue value) {
        if (field.isMap() && value instanceof FieldValue.MapValue mapValue) {
            encodeMapField(out, field, mapValue);
            return;
        }

        if (field.repeated() && value instanceof FieldValue.RepeatedValue repeated) {
            if (field.packed()) {
                encodePackedRepeatedField(out, field, repeated);
            } else {
                for (var v : repeated.values()) {
                    encodeSingleField(out, field.number(), field.type(), v);
                }
            }
            return;
        }

        encodeSingleField(out, field.number(), field.type(), value);
    }

    private static void encodeSingleField(ByteArrayOutputStream out, int fieldNumber,
                                           FieldDescriptor.Type type, FieldValue value) {
        WireType wireType = type.wireType();
        writeVarint(out, new FieldTag(fieldNumber, wireType).encode());

        switch (value) {
            case FieldValue.VarintValue varint -> {
                long v = varint.value();
                if (type == FieldDescriptor.Type.SINT32) {
                    v = zigzagEncode((int) v);
                } else if (type == FieldDescriptor.Type.SINT64) {
                    v = zigzagEncode(v);
                }
                writeVarint(out, v);
            }
            case FieldValue.Fixed64Value f64 -> writeFixed64(out, f64.value());
            case FieldValue.Fixed32Value f32 -> writeFixed32(out, f32.value());
            case FieldValue.BytesValue bytes -> {
                writeVarint(out, bytes.value().length);
                out.writeBytes(bytes.value());
            }
            case FieldValue.MessageValue msg -> {
                byte[] encoded = encode(msg.message());
                writeVarint(out, encoded.length);
                out.writeBytes(encoded);
            }
            default -> throw new IllegalArgumentException("Unexpected value type: " + value.getClass());
        }
    }

    private static void encodePackedRepeatedField(ByteArrayOutputStream out,
                                                    FieldDescriptor field,
                                                    FieldValue.RepeatedValue repeated) {
        var packedData = new ByteArrayOutputStream();
        for (var v : repeated.values()) {
            switch (v) {
                case FieldValue.VarintValue varint -> {
                    long val = varint.value();
                    if (field.type() == FieldDescriptor.Type.SINT32) {
                        val = zigzagEncode((int) val);
                    } else if (field.type() == FieldDescriptor.Type.SINT64) {
                        val = zigzagEncode(val);
                    }
                    writeVarint(packedData, val);
                }
                case FieldValue.Fixed32Value f32 -> writeFixed32(packedData, f32.value());
                case FieldValue.Fixed64Value f64 -> writeFixed64(packedData, f64.value());
                default -> throw new IllegalArgumentException("Cannot pack: " + v.getClass());
            }
        }
        writeVarint(out, new FieldTag(field.number(), WireType.LENGTH_DELIMITED).encode());
        byte[] data = packedData.toByteArray();
        writeVarint(out, data.length);
        out.writeBytes(data);
    }

    private static void encodeMapField(ByteArrayOutputStream out, FieldDescriptor field,
                                        FieldValue.MapValue mapValue) {
        for (var entry : mapValue.entries()) {
            var entryOut = new ByteArrayOutputStream();
            var keyValue = entry.get(1);
            var valValue = entry.get(2);
            if (keyValue != null) {
                encodeSingleField(entryOut, 1, field.mapKey().type(), keyValue);
            }
            if (valValue != null) {
                encodeSingleField(entryOut, 2, field.mapValue().type(), valValue);
            }
            byte[] entryBytes = entryOut.toByteArray();
            writeVarint(out, new FieldTag(field.number(), WireType.LENGTH_DELIMITED).encode());
            writeVarint(out, entryBytes.length);
            out.writeBytes(entryBytes);
        }
    }

    private static void encodeFieldSchemaless(ByteArrayOutputStream out, int fieldNumber, FieldValue value) {
        switch (value) {
            case FieldValue.VarintValue varint -> {
                writeVarint(out, new FieldTag(fieldNumber, WireType.VARINT).encode());
                writeVarint(out, varint.value());
            }
            case FieldValue.Fixed64Value f64 -> {
                writeVarint(out, new FieldTag(fieldNumber, WireType.FIXED64).encode());
                writeFixed64(out, f64.value());
            }
            case FieldValue.Fixed32Value f32 -> {
                writeVarint(out, new FieldTag(fieldNumber, WireType.FIXED32).encode());
                writeFixed32(out, f32.value());
            }
            case FieldValue.BytesValue bytes -> {
                writeVarint(out, new FieldTag(fieldNumber, WireType.LENGTH_DELIMITED).encode());
                writeVarint(out, bytes.value().length);
                out.writeBytes(bytes.value());
            }
            case FieldValue.MessageValue msg -> {
                writeVarint(out, new FieldTag(fieldNumber, WireType.LENGTH_DELIMITED).encode());
                byte[] encoded = encode(msg.message());
                writeVarint(out, encoded.length);
                out.writeBytes(encoded);
            }
            case FieldValue.RepeatedValue repeated -> {
                for (var v : repeated.values()) {
                    encodeFieldSchemaless(out, fieldNumber, v);
                }
            }
            case FieldValue.MapValue mapValue -> {
                for (var entry : mapValue.entries()) {
                    writeVarint(out, new FieldTag(fieldNumber, WireType.LENGTH_DELIMITED).encode());
                    byte[] entryBytes = encode(entry);
                    writeVarint(out, entryBytes.length);
                    out.writeBytes(entryBytes);
                }
            }
        }
    }

    // ---- Decode ----

    /**
     * Decodes protobuf binary data into a ProtoMessage using the given descriptor.
     */
    public static ProtoMessage decode(byte[] data, MessageDescriptor descriptor) {
        return decode(ByteBuffer.wrap(data), descriptor);
    }

    public static ProtoMessage decode(ByteBuffer buf, MessageDescriptor descriptor) {
        var message = new ProtoMessage();
        var repeatedCollectors = new java.util.HashMap<Integer, List<FieldValue>>();

        while (buf.hasRemaining()) {
            int tagValue = (int) decodeVarint(buf);
            var tag = FieldTag.decode(tagValue);
            var field = descriptor != null ? descriptor.field(tag.fieldNumber()) : null;

            FieldValue decoded;
            if (field != null && field.packed() && tag.wireType() == WireType.LENGTH_DELIMITED) {
                var packedValues = decodePackedField(buf, field);
                repeatedCollectors.computeIfAbsent(tag.fieldNumber(), k -> new ArrayList<>()).addAll(packedValues);
                continue;
            }

            decoded = decodeValue(buf, tag.wireType(), field);

            if (field != null && field.isMap()) {
                var mapEntries = message.has(tag.fieldNumber())
                        ? new ArrayList<>(message.getMap(tag.fieldNumber()))
                        : new ArrayList<ProtoMessage>();
                if (decoded instanceof FieldValue.MessageValue mv) {
                    mapEntries.add(mv.message());
                } else if (decoded instanceof FieldValue.BytesValue bv) {
                    var entryMsg = decode(bv.value(), mapEntryDescriptor(field));
                    mapEntries.add(entryMsg);
                }
                message.setMap(tag.fieldNumber(), mapEntries);
                continue;
            }

            if (field != null && field.repeated()) {
                repeatedCollectors.computeIfAbsent(tag.fieldNumber(), k -> new ArrayList<>()).add(decoded);
                continue;
            }

            message.set(tag.fieldNumber(), decoded);
        }

        for (var entry : repeatedCollectors.entrySet()) {
            message.setRepeated(entry.getKey(), entry.getValue());
        }

        return message;
    }

    /**
     * Decodes protobuf binary data without a descriptor (schema-less).
     */
    public static ProtoMessage decode(byte[] data) {
        return decode(ByteBuffer.wrap(data), (MessageDescriptor) null);
    }

    private static List<FieldValue> decodePackedField(ByteBuffer buf, FieldDescriptor field) {
        int length = (int) decodeVarint(buf);
        int endPos = buf.position() + length;
        var values = new ArrayList<FieldValue>();
        while (buf.position() < endPos) {
            switch (field.type().wireType()) {
                case VARINT -> {
                    long v = decodeVarint(buf);
                    if (field.type() == FieldDescriptor.Type.SINT32) {
                        v = zigzagDecode((int) v);
                    } else if (field.type() == FieldDescriptor.Type.SINT64) {
                        v = zigzagDecode(v);
                    }
                    values.add(new FieldValue.VarintValue(v));
                }
                case FIXED32 -> values.add(new FieldValue.Fixed32Value(readFixed32(buf)));
                case FIXED64 -> values.add(new FieldValue.Fixed64Value(readFixed64(buf)));
                default -> throw new IllegalArgumentException("Cannot unpack wire type: " + field.type().wireType());
            }
        }
        return values;
    }

    private static FieldValue decodeValue(ByteBuffer buf, WireType wireType, FieldDescriptor field) {
        return switch (wireType) {
            case VARINT -> {
                long v = decodeVarint(buf);
                if (field != null) {
                    if (field.type() == FieldDescriptor.Type.SINT32) {
                        v = zigzagDecode((int) v);
                    } else if (field.type() == FieldDescriptor.Type.SINT64) {
                        v = zigzagDecode(v);
                    }
                }
                yield new FieldValue.VarintValue(v);
            }
            case FIXED64 -> new FieldValue.Fixed64Value(readFixed64(buf));
            case FIXED32 -> new FieldValue.Fixed32Value(readFixed32(buf));
            case LENGTH_DELIMITED -> {
                int length = (int) decodeVarint(buf);
                byte[] bytes = new byte[length];
                buf.get(bytes);
                if (field != null && field.isMessage() && field.messageDescriptor() != null) {
                    var nested = decode(bytes, field.messageDescriptor());
                    yield new FieldValue.MessageValue(nested);
                }
                yield new FieldValue.BytesValue(bytes);
            }
            case START_GROUP, END_GROUP ->
                    throw new UnsupportedOperationException("Group wire types are deprecated and not supported");
        };
    }

    private static MessageDescriptor mapEntryDescriptor(FieldDescriptor mapField) {
        return MessageDescriptor.builder(mapField.name() + "Entry")
                .addField(mapField.mapKey())
                .addField(mapField.mapValue())
                .build();
    }

    // ---- Low-level write helpers ----

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        out.writeBytes(encodeVarint(value));
    }

    private static void writeFixed32(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    private static void writeFixed64(ByteArrayOutputStream out, long value) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((value >> (i * 8)) & 0xFF));
        }
    }

    private static int readFixed32(ByteBuffer buf) {
        byte[] bytes = new byte[4];
        buf.get(bytes);
        return (bytes[0] & 0xFF)
                | ((bytes[1] & 0xFF) << 8)
                | ((bytes[2] & 0xFF) << 16)
                | ((bytes[3] & 0xFF) << 24);
    }

    private static long readFixed64(ByteBuffer buf) {
        byte[] bytes = new byte[8];
        buf.get(bytes);
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result |= (long) (bytes[i] & 0xFF) << (i * 8);
        }
        return result;
    }
}
