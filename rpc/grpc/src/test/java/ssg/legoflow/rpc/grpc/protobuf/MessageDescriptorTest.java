package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class MessageDescriptorTest {

    @Test
    void testBuildBasicDescriptor() {
        var descriptor = MessageDescriptor.builder("test.Person")
                .addField(FieldDescriptor.scalar(1, "name", FieldDescriptor.Type.STRING))
                .addField(FieldDescriptor.scalar(2, "age", FieldDescriptor.Type.INT32))
                .build();

        assertThat(descriptor.fullName()).isEqualTo("test.Person");
        assertThat(descriptor.field(1).name()).isEqualTo("name");
        assertThat(descriptor.field(2).name()).isEqualTo("age");
        assertThat(descriptor.field("name").number()).isEqualTo(1);
    }

    @Test
    void testFieldsCollection() {
        var descriptor = MessageDescriptor.builder("test.Msg")
                .addField(FieldDescriptor.scalar(1, "a", FieldDescriptor.Type.INT32))
                .addField(FieldDescriptor.scalar(2, "b", FieldDescriptor.Type.STRING))
                .addField(FieldDescriptor.scalar(3, "c", FieldDescriptor.Type.BOOL))
                .build();

        assertThat(descriptor.fields()).hasSize(3);
    }

    @Test
    void testOneofFields() {
        var descriptor = MessageDescriptor.builder("test.Union")
                .addOneof("value")
                .addField(FieldDescriptor.oneof(1, "str_val", FieldDescriptor.Type.STRING, 0))
                .addField(FieldDescriptor.oneof(2, "int_val", FieldDescriptor.Type.INT32, 0))
                .addField(FieldDescriptor.scalar(3, "name", FieldDescriptor.Type.STRING))
                .build();

        assertThat(descriptor.oneofNames()).containsExactly("value");
        assertThat(descriptor.oneofFields(0)).hasSize(2);
    }

    @Test
    void testFieldNotFound() {
        var descriptor = MessageDescriptor.builder("test.Empty").build();
        assertThat(descriptor.field(1)).isNull();
        assertThat(descriptor.field("missing")).isNull();
    }
}
