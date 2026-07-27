package ssg.legoflow.rpc.grpc.protobuf;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Sealed interface representing possible protobuf field values.
 */
public sealed interface FieldValue {

    /** A varint value (int32, int64, uint32, uint64, sint32, sint64, bool, enum). */
    record VarintValue(long value) implements FieldValue {}

    /** A 64-bit fixed value (fixed64, sfixed64, double). */
    record Fixed64Value(long value) implements FieldValue {
        public double asDouble() {
            return Double.longBitsToDouble(value);
        }

        public static Fixed64Value fromDouble(double d) {
            return new Fixed64Value(Double.doubleToRawLongBits(d));
        }
    }

    /** A 32-bit fixed value (fixed32, sfixed32, float). */
    record Fixed32Value(int value) implements FieldValue {
        public float asFloat() {
            return Float.intBitsToFloat(value);
        }

        public static Fixed32Value fromFloat(float f) {
            return new Fixed32Value(Float.floatToRawIntBits(f));
        }
    }

    /** A length-delimited value (string, bytes, embedded message). */
    record BytesValue(byte[] value) implements FieldValue {
        public String asString() {
            return new String(value, StandardCharsets.UTF_8);
        }

        public static BytesValue fromString(String s) {
            return new BytesValue(s.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** A repeated field value (list of values). */
    record RepeatedValue(List<FieldValue> values) implements FieldValue {
        public RepeatedValue {
            values = List.copyOf(values);
        }
    }

    /** A map field value (list of key-value entry messages). */
    record MapValue(List<ProtoMessage> entries) implements FieldValue {
        public MapValue {
            entries = List.copyOf(entries);
        }
    }

    /** An embedded message value. */
    record MessageValue(ProtoMessage message) implements FieldValue {}
}
