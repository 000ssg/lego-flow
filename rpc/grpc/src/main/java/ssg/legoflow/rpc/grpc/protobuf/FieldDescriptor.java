package ssg.legoflow.rpc.grpc.protobuf;

/**
 * Descriptor for a single protobuf field, providing metadata for encoding/decoding.
 *
 * @param number     the field number
 * @param name       the field name
 * @param type       the protobuf field type
 * @param repeated   whether this field is repeated
 * @param packed     whether this repeated field uses packed encoding
 * @param mapKey     for map fields, the descriptor of the key type (null if not a map)
 * @param mapValue   for map fields, the descriptor of the value type (null if not a map)
 * @param messageDescriptor for message-typed fields, the nested message descriptor
 * @param oneofIndex the oneof group index (-1 if not part of a oneof)
 */
public record FieldDescriptor(
        int number,
        String name,
        Type type,
        boolean repeated,
        boolean packed,
        FieldDescriptor mapKey,
        FieldDescriptor mapValue,
        MessageDescriptor messageDescriptor,
        int oneofIndex
) {

    /**
     * Protobuf field types.
     */
    public enum Type {
        INT32(WireType.VARINT),
        INT64(WireType.VARINT),
        UINT32(WireType.VARINT),
        UINT64(WireType.VARINT),
        SINT32(WireType.VARINT),
        SINT64(WireType.VARINT),
        BOOL(WireType.VARINT),
        ENUM(WireType.VARINT),
        FIXED32(WireType.FIXED32),
        SFIXED32(WireType.FIXED32),
        FLOAT(WireType.FIXED32),
        FIXED64(WireType.FIXED64),
        SFIXED64(WireType.FIXED64),
        DOUBLE(WireType.FIXED64),
        STRING(WireType.LENGTH_DELIMITED),
        BYTES(WireType.LENGTH_DELIMITED),
        MESSAGE(WireType.LENGTH_DELIMITED),
        MAP(WireType.LENGTH_DELIMITED);

        private final WireType wireType;

        Type(WireType wireType) {
            this.wireType = wireType;
        }

        public WireType wireType() {
            return wireType;
        }
    }

    /**
     * Creates a simple scalar field descriptor.
     */
    public static FieldDescriptor scalar(int number, String name, Type type) {
        return new FieldDescriptor(number, name, type, false, false, null, null, null, -1);
    }

    /**
     * Creates a repeated field descriptor.
     */
    public static FieldDescriptor repeated(int number, String name, Type type, boolean packed) {
        return new FieldDescriptor(number, name, type, true, packed, null, null, null, -1);
    }

    /**
     * Creates a message field descriptor.
     */
    public static FieldDescriptor message(int number, String name, MessageDescriptor descriptor) {
        return new FieldDescriptor(number, name, Type.MESSAGE, false, false, null, null, descriptor, -1);
    }

    /**
     * Creates a repeated message field descriptor.
     */
    public static FieldDescriptor repeatedMessage(int number, String name, MessageDescriptor descriptor) {
        return new FieldDescriptor(number, name, Type.MESSAGE, true, false, null, null, descriptor, -1);
    }

    /**
     * Creates a map field descriptor.
     */
    public static FieldDescriptor map(int number, String name,
                                       FieldDescriptor keyDescriptor, FieldDescriptor valueDescriptor) {
        return new FieldDescriptor(number, name, Type.MAP, true, false,
                keyDescriptor, valueDescriptor, null, -1);
    }

    /**
     * Creates a oneof field descriptor.
     */
    public static FieldDescriptor oneof(int number, String name, Type type, int oneofIndex) {
        return new FieldDescriptor(number, name, type, false, false, null, null, null, oneofIndex);
    }

    /**
     * Creates a oneof message field descriptor.
     */
    public static FieldDescriptor oneofMessage(int number, String name,
                                                MessageDescriptor descriptor, int oneofIndex) {
        return new FieldDescriptor(number, name, Type.MESSAGE, false, false, null, null, descriptor, oneofIndex);
    }

    public boolean isMap() {
        return type == Type.MAP;
    }

    public boolean isOneof() {
        return oneofIndex >= 0;
    }

    public boolean isMessage() {
        return type == Type.MESSAGE;
    }

    public WireType wireType() {
        if (repeated && packed) {
            return WireType.LENGTH_DELIMITED;
        }
        return type.wireType();
    }
}
