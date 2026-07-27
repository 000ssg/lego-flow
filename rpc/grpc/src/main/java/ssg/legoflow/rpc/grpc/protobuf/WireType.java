package ssg.legoflow.rpc.grpc.protobuf;

/**
 * Protobuf wire types as defined in the Protocol Buffers encoding specification.
 */
public enum WireType {

    /** Varint: int32, int64, uint32, uint64, sint32, sint64, bool, enum. */
    VARINT(0),

    /** 64-bit: fixed64, sfixed64, double. */
    FIXED64(1),

    /** Length-delimited: string, bytes, embedded messages, packed repeated fields. */
    LENGTH_DELIMITED(2),

    /** Start group (deprecated). */
    START_GROUP(3),

    /** End group (deprecated). */
    END_GROUP(4),

    /** 32-bit: fixed32, sfixed32, float. */
    FIXED32(5);

    private final int value;

    WireType(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    public static WireType fromValue(int value) {
        return switch (value) {
            case 0 -> VARINT;
            case 1 -> FIXED64;
            case 2 -> LENGTH_DELIMITED;
            case 3 -> START_GROUP;
            case 4 -> END_GROUP;
            case 5 -> FIXED32;
            default -> throw new IllegalArgumentException("Unknown wire type: " + value);
        };
    }
}
