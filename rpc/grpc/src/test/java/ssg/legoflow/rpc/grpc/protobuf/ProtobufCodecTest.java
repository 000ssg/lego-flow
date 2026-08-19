package ssg.legoflow.rpc.grpc.protobuf;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ProtobufCodecTest {

    @Nested
    class VarintTests {

        @Test
        void testEncodeZero() {
            byte[] encoded = ProtobufCodec.encodeVarint(0);
            assertThat(encoded).containsExactly(0x00);
        }

        @Test
        void testEncodeOne() {
            byte[] encoded = ProtobufCodec.encodeVarint(1);
            assertThat(encoded).containsExactly(0x01);
        }

        @Test
        void testEncode127() {
            byte[] encoded = ProtobufCodec.encodeVarint(127);
            assertThat(encoded).containsExactly(0x7F);
        }

        @Test
        void testEncode128() {
            byte[] encoded = ProtobufCodec.encodeVarint(128);
            assertThat(encoded).hasSize(2);
        }

        @Test
        void testEncode300() {
            byte[] encoded = ProtobufCodec.encodeVarint(300);
            assertThat(encoded).containsExactly(0xAC, 0x02);
        }

        @Test
        void testEncodeMaxInt() {
            byte[] encoded = ProtobufCodec.encodeVarint(Integer.MAX_VALUE);
            assertThat(encoded.length).isGreaterThanOrEqualTo(1);
            long decoded = ProtobufCodec.decodeVarint(ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(Integer.MAX_VALUE);
        }

        @Test
        void testEncodeMaxLong() {
            byte[] encoded = ProtobufCodec.encodeVarint(Long.MAX_VALUE);
            long decoded = ProtobufCodec.decodeVarint(ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        void testDecodeRoundTrip() {
            for (long v : new long[]{0, 1, 127, 128, 300, 16384, Integer.MAX_VALUE, Long.MAX_VALUE}) {
                byte[] encoded = ProtobufCodec.encodeVarint(v);
                long decoded = ProtobufCodec.decodeVarint(ByteBuffer.wrap(encoded));
                assertThat(decoded).isEqualTo(v);
            }
        }

        @Test
        void testDecodeEmptyBuffer() {
            assertThatThrownBy(() -> ProtobufCodec.decodeVarint(ByteBuffer.allocate(0)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testDecodeNegativeAsUnsigned() {
            byte[] encoded = ProtobufCodec.encodeVarint(-1L);
            long decoded = ProtobufCodec.decodeVarint(ByteBuffer.wrap(encoded));
            assertThat(decoded).isEqualTo(-1L);
        }
    }

    @Nested
    class ZigZagTests {

        @Test
        void testZigZagEncodeZero() {
            assertThat(ProtobufCodec.zigzagEncode(0)).isEqualTo(0);
        }

        @Test
        void testZigZagEncodeMinusOne() {
            assertThat(ProtobufCodec.zigzagEncode(-1)).isEqualTo(1);
        }

        @Test
        void testZigZagEncodeOne() {
            assertThat(ProtobufCodec.zigzagEncode(1)).isEqualTo(2);
        }

        @Test
        void testZigZagEncodeMinusTwo() {
            assertThat(ProtobufCodec.zigzagEncode(-2)).isEqualTo(3);
        }

        @Test
        void testZigZagDecodeRoundTrip() {
            for (int v : new int[]{0, 1, -1, 2, -2, 100, -100, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
                int encoded = ProtobufCodec.zigzagEncode(v);
                int decoded = ProtobufCodec.zigzagDecode(encoded);
                assertThat(decoded).isEqualTo(v);
            }
        }

        @Test
        void testZigZagLong() {
            for (long v : new long[]{0L, 1L, -1L, Long.MAX_VALUE, Long.MIN_VALUE}) {
                long encoded = ProtobufCodec.zigzagEncode(v);
                long decoded = ProtobufCodec.zigzagDecode(encoded);
                assertThat(decoded).isEqualTo(v);
            }
        }

        @Test
        void testZigZagEncodeMinInt() {
            int encoded = ProtobufCodec.zigzagEncode(Integer.MIN_VALUE);
            // MIN_VALUE zigzag encodes to unsigned max int (0xFFFFFFFF = -1 in signed Java int)
            assertThat(encoded).isEqualTo(-1);
            assertThat(ProtobufCodec.zigzagDecode(encoded)).isEqualTo(Integer.MIN_VALUE);
        }

        @Test
        void testZigZagEncodeLargePositive() {
            assertThat(ProtobufCodec.zigzagEncode(2147483647)).isEqualTo(-2);
        }
    }

    @Nested
    class SchemalessEncodeDecodeTests {

        @Test
        void testEncodeDecodeVarintField() {
            var msg = new ProtoMessage().setVarint(1, 42);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(decoded.getVarint(1)).isEqualTo(42);
        }

        @Test
        void testEncodeDecodeStringField() {
            var msg = new ProtoMessage().setString(1, "hello");
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(decoded.get(1)).isInstanceOf(FieldValue.BytesValue.class);
            assertThat(((FieldValue.BytesValue) decoded.get(1)).asString()).isEqualTo("hello");
        }

        @Test
        void testEncodeDecodeMultipleFields() {
            var msg = new ProtoMessage()
                    .setVarint(1, 100)
                    .setString(2, "test")
                    .setVarint(3, 200);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(decoded.getVarint(1)).isEqualTo(100);
            assertThat(((FieldValue.BytesValue) decoded.get(2)).asString()).isEqualTo("test");
            assertThat(decoded.getVarint(3)).isEqualTo(200);
        }

        @Test
        void testEncodeDecodeFixed32() {
            var msg = new ProtoMessage().setFixed32(1, 12345);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(((FieldValue.Fixed32Value) decoded.get(1)).value()).isEqualTo(12345);
        }

        @Test
        void testEncodeDecodeFixed64() {
            var msg = new ProtoMessage().setFixed64(1, 123456789L);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(((FieldValue.Fixed64Value) decoded.get(1)).value()).isEqualTo(123456789L);
        }

        @Test
        void testEncodeDecodeFloat() {
            var msg = new ProtoMessage().setFloat(1, 3.14f);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(((FieldValue.Fixed32Value) decoded.get(1)).asFloat()).isEqualTo(3.14f);
        }

        @Test
        void testEncodeDecodeDouble() {
            var msg = new ProtoMessage().setDouble(1, 2.71828);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(((FieldValue.Fixed64Value) decoded.get(1)).asDouble()).isEqualTo(2.71828);
        }

        @Test
        void testEncodeDecodeBool() {
            var msg = new ProtoMessage().setBool(1, true).setBool(2, false);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(decoded.getVarint(1)).isEqualTo(1);
            assertThat(decoded.getVarint(2)).isEqualTo(0);
        }

        @Test
        void testEncodeDecodeNestedMessage() {
            var inner = new ProtoMessage().setVarint(1, 99);
            var outer = new ProtoMessage().setMessage(1, inner);
            byte[] encoded = ProtobufCodec.encode(outer);
            var decoded = ProtobufCodec.decode(encoded);
            // Without schema, nested message comes back as BytesValue
            assertThat(decoded.get(1)).isInstanceOf(FieldValue.BytesValue.class);
        }

        @Test
        void testEncodeDecodeEmptyMessage() {
            var msg = new ProtoMessage();
            byte[] encoded = ProtobufCodec.encode(msg);
            assertThat(encoded).isEmpty();
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(decoded.fieldCount()).isEqualTo(0);
        }

        @Test
        void testEncodeDecodeBytes() {
            byte[] data = {0x01, 0x02, 0x03, 0x04};
            var msg = new ProtoMessage().setBytes(1, data);
            byte[] encoded = ProtobufCodec.encode(msg);
            var decoded = ProtobufCodec.decode(encoded);
            assertThat(((FieldValue.BytesValue) decoded.get(1)).value()).containsExactly(data);
        }
    }

    @Nested
    class DescriptorBasedTests {

        @Test
        void testEncodeDecodeWithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestMessage")
                    .addField(FieldDescriptor.scalar(1, "name", FieldDescriptor.Type.STRING))
                    .addField(FieldDescriptor.scalar(2, "age", FieldDescriptor.Type.INT32))
                    .build();

            var msg = new ProtoMessage()
                    .setString(1, "Alice")
                    .setVarint(2, 30);

            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            assertThat(decoded.getString(1)).isEqualTo("Alice");
            assertThat(decoded.getInt32(2)).isEqualTo(30);
        }

        @Test
        void testSint32WithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestSint")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.SINT32))
                    .build();

            var msg = new ProtoMessage().setVarint(1, -42);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);
            assertThat(decoded.getInt32(1)).isEqualTo(-42);
        }

        @Test
        void testSint64WithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestSint64")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.SINT64))
                    .build();

            var msg = new ProtoMessage().setVarint(1, -1000000L);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);
            assertThat(decoded.getVarint(1)).isEqualTo(-1000000L);
        }

        @Test
        void testNestedMessageWithDescriptor() {
            var innerDescriptor = MessageDescriptor.builder("Inner")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.INT32))
                    .build();

            var outerDescriptor = MessageDescriptor.builder("Outer")
                    .addField(FieldDescriptor.message(1, "inner", innerDescriptor))
                    .addField(FieldDescriptor.scalar(2, "name", FieldDescriptor.Type.STRING))
                    .build();

            var inner = new ProtoMessage().setVarint(1, 42);
            var outer = new ProtoMessage()
                    .setMessage(1, inner)
                    .setString(2, "test");

            byte[] encoded = ProtobufCodec.encode(outer, outerDescriptor);
            var decoded = ProtobufCodec.decode(encoded, outerDescriptor);

            assertThat(decoded.getMessage(1).getInt32(1)).isEqualTo(42);
            assertThat(decoded.getString(2)).isEqualTo("test");
        }

        @Test
        void testRepeatedField() {
            var descriptor = MessageDescriptor.builder("TestRepeated")
                    .addField(FieldDescriptor.repeated(1, "values", FieldDescriptor.Type.INT32, false))
                    .build();

            var values = List.<FieldValue>of(
                    new FieldValue.VarintValue(1),
                    new FieldValue.VarintValue(2),
                    new FieldValue.VarintValue(3)
            );
            var msg = new ProtoMessage().setRepeated(1, values);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(3);
            assertThat(((FieldValue.VarintValue) repeated.get(0)).value()).isEqualTo(1);
            assertThat(((FieldValue.VarintValue) repeated.get(1)).value()).isEqualTo(2);
            assertThat(((FieldValue.VarintValue) repeated.get(2)).value()).isEqualTo(3);
        }

        @Test
        void testPackedRepeatedField() {
            var descriptor = MessageDescriptor.builder("TestPacked")
                    .addField(FieldDescriptor.repeated(1, "values", FieldDescriptor.Type.INT32, true))
                    .build();

            var values = List.<FieldValue>of(
                    new FieldValue.VarintValue(10),
                    new FieldValue.VarintValue(20),
                    new FieldValue.VarintValue(30),
                    new FieldValue.VarintValue(40),
                    new FieldValue.VarintValue(50)
            );
            var msg = new ProtoMessage().setRepeated(1, values);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(5);
            assertThat(((FieldValue.VarintValue) repeated.get(0)).value()).isEqualTo(10);
            assertThat(((FieldValue.VarintValue) repeated.get(4)).value()).isEqualTo(50);
        }

        @Test
        void testPackedFixed32() {
            var descriptor = MessageDescriptor.builder("TestPackedFixed")
                    .addField(FieldDescriptor.repeated(1, "values", FieldDescriptor.Type.FIXED32, true))
                    .build();

            var values = List.<FieldValue>of(
                    new FieldValue.Fixed32Value(100),
                    new FieldValue.Fixed32Value(200),
                    new FieldValue.Fixed32Value(300)
            );
            var msg = new ProtoMessage().setRepeated(1, values);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(3);
            assertThat(((FieldValue.Fixed32Value) repeated.get(0)).value()).isEqualTo(100);
        }

        @Test
        void testPackedFixed64() {
            var descriptor = MessageDescriptor.builder("TestPackedFixed64")
                    .addField(FieldDescriptor.repeated(1, "values", FieldDescriptor.Type.FIXED64, true))
                    .build();

            var values = List.<FieldValue>of(
                    new FieldValue.Fixed64Value(1000L),
                    new FieldValue.Fixed64Value(2000L)
            );
            var msg = new ProtoMessage().setRepeated(1, values);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(2);
            assertThat(((FieldValue.Fixed64Value) repeated.get(1)).value()).isEqualTo(2000L);
        }

        @Test
        void testMapField() {
            var keyDesc = FieldDescriptor.scalar(1, "key", FieldDescriptor.Type.STRING);
            var valueDesc = FieldDescriptor.scalar(2, "value", FieldDescriptor.Type.INT32);
            var descriptor = MessageDescriptor.builder("TestMap")
                    .addField(FieldDescriptor.map(1, "entries", keyDesc, valueDesc))
                    .build();

            var entries = List.of(
                    new ProtoMessage().setString(1, "a").setVarint(2, 1),
                    new ProtoMessage().setString(1, "b").setVarint(2, 2)
            );
            var msg = new ProtoMessage().setMap(1, entries);

            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var mapEntries = decoded.getMap(1);
            assertThat(mapEntries).hasSize(2);
        }

        @Test
        void testOneofField() {
            var descriptor = MessageDescriptor.builder("TestOneof")
                    .addOneof("value")
                    .addField(FieldDescriptor.oneof(1, "str_value", FieldDescriptor.Type.STRING, 0))
                    .addField(FieldDescriptor.oneof(2, "int_value", FieldDescriptor.Type.INT32, 0))
                    .build();

            // Set only the string value (oneof)
            var msg = new ProtoMessage().setString(1, "hello");
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            assertThat(decoded.has(1)).isTrue();
            assertThat(decoded.has(2)).isFalse();
            assertThat(decoded.getString(1)).isEqualTo("hello");
        }

        @Test
        void testRepeatedMessages() {
            var itemDesc = MessageDescriptor.builder("Item")
                    .addField(FieldDescriptor.scalar(1, "id", FieldDescriptor.Type.INT32))
                    .addField(FieldDescriptor.scalar(2, "name", FieldDescriptor.Type.STRING))
                    .build();

            var descriptor = MessageDescriptor.builder("Container")
                    .addField(FieldDescriptor.repeatedMessage(1, "items", itemDesc))
                    .build();

            var items = List.<FieldValue>of(
                    new FieldValue.MessageValue(new ProtoMessage().setVarint(1, 1).setString(2, "a")),
                    new FieldValue.MessageValue(new ProtoMessage().setVarint(1, 2).setString(2, "b"))
            );
            var msg = new ProtoMessage().setRepeated(1, items);

            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(2);
            var first = ((FieldValue.MessageValue) repeated.get(0)).message();
            assertThat(first.getInt32(1)).isEqualTo(1);
            assertThat(first.getString(2)).isEqualTo("a");
        }

        @Test
        void testDoubleFieldWithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestDouble")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.DOUBLE))
                    .build();

            var msg = new ProtoMessage().setDouble(1, Math.PI);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);
            assertThat(decoded.getDouble(1)).isEqualTo(Math.PI);
        }

        @Test
        void testFloatFieldWithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestFloat")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.FLOAT))
                    .build();

            var msg = new ProtoMessage().setFloat(1, 2.5f);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);
            assertThat(decoded.getFloat(1)).isEqualTo(2.5f);
        }

        @Test
        void testUint32WithDescriptor() {
            var descriptor = MessageDescriptor.builder("TestUint32")
                    .addField(FieldDescriptor.scalar(1, "value", FieldDescriptor.Type.UINT32))
                    .build();

            var msg = new ProtoMessage().setVarint(1, 4294967295L);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);
            assertThat(decoded.getVarint(1)).isEqualTo(4294967295L);
        }

        @Test
        void testPackedSint32() {
            var descriptor = MessageDescriptor.builder("TestPackedSint")
                    .addField(FieldDescriptor.repeated(1, "values", FieldDescriptor.Type.SINT32, true))
                    .build();

            var values = List.<FieldValue>of(
                    new FieldValue.VarintValue(-1),
                    new FieldValue.VarintValue(0),
                    new FieldValue.VarintValue(1),
                    new FieldValue.VarintValue(-100)
            );
            var msg = new ProtoMessage().setRepeated(1, values);
            byte[] encoded = ProtobufCodec.encode(msg, descriptor);
            var decoded = ProtobufCodec.decode(encoded, descriptor);

            var repeated = decoded.getRepeated(1);
            assertThat(repeated).hasSize(4);
            assertThat(((FieldValue.VarintValue) repeated.get(0)).value()).isEqualTo(-1);
            assertThat(((FieldValue.VarintValue) repeated.get(3)).value()).isEqualTo(-100);
        }
    }

    @Nested
    class ProtoMessageTests {

        @Test
        void testFieldNumbers() {
            var msg = new ProtoMessage()
                    .setVarint(1, 1)
                    .setString(2, "test")
                    .setVarint(3, 3);
            assertThat(msg.fieldNumbers()).containsExactlyInAnyOrder(1, 2, 3);
        }

        @Test
        void testHasField() {
            var msg = new ProtoMessage().setVarint(1, 42);
            assertThat(msg.has(1)).isTrue();
            assertThat(msg.has(2)).isFalse();
        }

        @Test
        void testFieldCount() {
            var msg = new ProtoMessage()
                    .setVarint(1, 1)
                    .setVarint(2, 2);
            assertThat(msg.fieldCount()).isEqualTo(2);
        }

        @Test
        void testGetBool() {
            var msg = new ProtoMessage().setBool(1, true).setBool(2, false);
            assertThat(msg.getBool(1)).isTrue();
            assertThat(msg.getBool(2)).isFalse();
        }

        @Test
        void testGetRepeatedDefault() {
            var msg = new ProtoMessage();
            assertThat(msg.getRepeated(99)).isEmpty();
        }

        @Test
        void testGetMapDefault() {
            var msg = new ProtoMessage();
            assertThat(msg.getMap(99)).isEmpty();
        }

        @Test
        void testGetVarintWrongType() {
            var msg = new ProtoMessage().setString(1, "hello");
            assertThatThrownBy(() -> msg.getVarint(1))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void testGetStringWrongType() {
            var msg = new ProtoMessage().setVarint(1, 42);
            assertThatThrownBy(() -> msg.getString(1))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
