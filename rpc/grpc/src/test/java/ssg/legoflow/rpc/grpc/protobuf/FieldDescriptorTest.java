package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FieldDescriptorTest {

    @Test
    void testScalarField() {
        var field = FieldDescriptor.scalar(1, "name", FieldDescriptor.Type.STRING);
        assertThat(field.number()).isEqualTo(1);
        assertThat(field.name()).isEqualTo("name");
        assertThat(field.type()).isEqualTo(FieldDescriptor.Type.STRING);
        assertThat(field.repeated()).isFalse();
        assertThat(field.packed()).isFalse();
        assertThat(field.isMap()).isFalse();
        assertThat(field.isOneof()).isFalse();
    }

    @Test
    void testRepeatedField() {
        var field = FieldDescriptor.repeated(1, "ids", FieldDescriptor.Type.INT32, true);
        assertThat(field.repeated()).isTrue();
        assertThat(field.packed()).isTrue();
        assertThat(field.wireType()).isEqualTo(WireType.LENGTH_DELIMITED);
    }

    @Test
    void testMessageField() {
        var innerDesc = MessageDescriptor.builder("Inner").build();
        var field = FieldDescriptor.message(1, "inner", innerDesc);
        assertThat(field.isMessage()).isTrue();
        assertThat(field.messageDescriptor()).isEqualTo(innerDesc);
    }

    @Test
    void testMapField() {
        var keyDesc = FieldDescriptor.scalar(1, "key", FieldDescriptor.Type.STRING);
        var valueDesc = FieldDescriptor.scalar(2, "value", FieldDescriptor.Type.INT32);
        var field = FieldDescriptor.map(1, "entries", keyDesc, valueDesc);
        assertThat(field.isMap()).isTrue();
        assertThat(field.mapKey()).isEqualTo(keyDesc);
        assertThat(field.mapValue()).isEqualTo(valueDesc);
    }

    @Test
    void testOneofField() {
        var field = FieldDescriptor.oneof(1, "str_value", FieldDescriptor.Type.STRING, 0);
        assertThat(field.isOneof()).isTrue();
        assertThat(field.oneofIndex()).isEqualTo(0);
    }

    @Test
    void testWireTypeForTypes() {
        assertThat(FieldDescriptor.Type.INT32.wireType()).isEqualTo(WireType.VARINT);
        assertThat(FieldDescriptor.Type.INT64.wireType()).isEqualTo(WireType.VARINT);
        assertThat(FieldDescriptor.Type.BOOL.wireType()).isEqualTo(WireType.VARINT);
        assertThat(FieldDescriptor.Type.DOUBLE.wireType()).isEqualTo(WireType.FIXED64);
        assertThat(FieldDescriptor.Type.FLOAT.wireType()).isEqualTo(WireType.FIXED32);
        assertThat(FieldDescriptor.Type.STRING.wireType()).isEqualTo(WireType.LENGTH_DELIMITED);
        assertThat(FieldDescriptor.Type.BYTES.wireType()).isEqualTo(WireType.LENGTH_DELIMITED);
        assertThat(FieldDescriptor.Type.MESSAGE.wireType()).isEqualTo(WireType.LENGTH_DELIMITED);
    }

    @Test
    void testRepeatedMessageField() {
        var itemDesc = MessageDescriptor.builder("Item").build();
        var field = FieldDescriptor.repeatedMessage(1, "items", itemDesc);
        assertThat(field.repeated()).isTrue();
        assertThat(field.isMessage()).isTrue();
        assertThat(field.packed()).isFalse();
    }

    @Test
    void testOneofMessageField() {
        var msgDesc = MessageDescriptor.builder("Msg").build();
        var field = FieldDescriptor.oneofMessage(1, "msg", msgDesc, 0);
        assertThat(field.isOneof()).isTrue();
        assertThat(field.isMessage()).isTrue();
        assertThat(field.oneofIndex()).isEqualTo(0);
    }
}
