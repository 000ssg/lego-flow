package ssg.legoflow.rpc.grpc.protobuf;

import java.util.*;

/**
 * A dynamic protobuf message represented as a map of field numbers to field values.
 * Supports schema-less operation for runtime descriptor-based gRPC.
 */
public class ProtoMessage {

    private final Map<Integer, FieldValue> fields = new LinkedHashMap<>();

    public ProtoMessage() {
    }

    public ProtoMessage set(int fieldNumber, FieldValue value) {
        fields.put(fieldNumber, value);
        return this;
    }

    public ProtoMessage setVarint(int fieldNumber, long value) {
        return set(fieldNumber, new FieldValue.VarintValue(value));
    }

    public ProtoMessage setString(int fieldNumber, String value) {
        return set(fieldNumber, FieldValue.BytesValue.fromString(value));
    }

    public ProtoMessage setBytes(int fieldNumber, byte[] value) {
        return set(fieldNumber, new FieldValue.BytesValue(value));
    }

    public ProtoMessage setDouble(int fieldNumber, double value) {
        return set(fieldNumber, FieldValue.Fixed64Value.fromDouble(value));
    }

    public ProtoMessage setFloat(int fieldNumber, float value) {
        return set(fieldNumber, FieldValue.Fixed32Value.fromFloat(value));
    }

    public ProtoMessage setFixed32(int fieldNumber, int value) {
        return set(fieldNumber, new FieldValue.Fixed32Value(value));
    }

    public ProtoMessage setFixed64(int fieldNumber, long value) {
        return set(fieldNumber, new FieldValue.Fixed64Value(value));
    }

    public ProtoMessage setBool(int fieldNumber, boolean value) {
        return set(fieldNumber, new FieldValue.VarintValue(value ? 1 : 0));
    }

    public ProtoMessage setMessage(int fieldNumber, ProtoMessage nested) {
        return set(fieldNumber, new FieldValue.MessageValue(nested));
    }

    public ProtoMessage setRepeated(int fieldNumber, List<FieldValue> values) {
        return set(fieldNumber, new FieldValue.RepeatedValue(values));
    }

    public ProtoMessage setMap(int fieldNumber, List<ProtoMessage> entries) {
        return set(fieldNumber, new FieldValue.MapValue(entries));
    }

    public FieldValue get(int fieldNumber) {
        return fields.get(fieldNumber);
    }

    public long getVarint(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.VarintValue varint) {
            return varint.value();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not a varint");
    }

    public int getInt32(int fieldNumber) {
        return (int) getVarint(fieldNumber);
    }

    public boolean getBool(int fieldNumber) {
        return getVarint(fieldNumber) != 0;
    }

    public String getString(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.BytesValue bytes) {
            return bytes.asString();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not bytes/string");
    }

    public byte[] getBytes(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.BytesValue bytes) {
            return bytes.value();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not bytes");
    }

    public double getDouble(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.Fixed64Value f64) {
            return f64.asDouble();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not fixed64/double");
    }

    public float getFloat(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.Fixed32Value f32) {
            return f32.asFloat();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not fixed32/float");
    }

    public ProtoMessage getMessage(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.MessageValue msg) {
            return msg.message();
        }
        throw new IllegalStateException("Field " + fieldNumber + " is not a message");
    }

    public List<FieldValue> getRepeated(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.RepeatedValue rep) {
            return rep.values();
        }
        return List.of();
    }

    public List<ProtoMessage> getMap(int fieldNumber) {
        var v = fields.get(fieldNumber);
        if (v instanceof FieldValue.MapValue map) {
            return map.entries();
        }
        return List.of();
    }

    public boolean has(int fieldNumber) {
        return fields.containsKey(fieldNumber);
    }

    public Set<Integer> fieldNumbers() {
        return Collections.unmodifiableSet(fields.keySet());
    }

    public Map<Integer, FieldValue> toMap() {
        return Collections.unmodifiableMap(fields);
    }

    public int fieldCount() {
        return fields.size();
    }

    @Override
    public String toString() {
        return "ProtoMessage" + fields;
    }
}
