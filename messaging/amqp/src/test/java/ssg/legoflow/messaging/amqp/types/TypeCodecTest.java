package ssg.legoflow.messaging.amqp.types;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.common.AmqpException;
import java.nio.ByteBuffer;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link TypeCodec} — AMQP type system encoding/decoding.
 */
class TypeCodecTest {

    // ---- Null ----

    @Test void testNullRoundTrip() {
        assertRoundTrip(new AmqpType.Null());
    }

    @Test void testNullEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Null());
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get()).isEqualTo(TypeCodec.NULL);
    }

    // ---- Boolean ----

    @Test void testBooleanTrueRoundTrip() {
        assertRoundTrip(new AmqpType.Bool(true));
    }

    @Test void testBooleanFalseRoundTrip() {
        assertRoundTrip(new AmqpType.Bool(false));
    }

    @Test void testBooleanTrueEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Bool(true));
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get()).isEqualTo(TypeCodec.BOOLEAN_TRUE);
    }

    @Test void testBooleanFalseEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Bool(false));
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get()).isEqualTo(TypeCodec.BOOLEAN_FALSE);
    }

    // ---- UByte ----

    @Test void testUByteRoundTrip() {
        assertRoundTrip(new AmqpType.UByte((short) 0));
        assertRoundTrip(new AmqpType.UByte((short) 127));
        assertRoundTrip(new AmqpType.UByte((short) 255));
    }

    @Test void testUByteRangeValidation() {
        assertThatThrownBy(() -> new AmqpType.UByte((short) -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AmqpType.UByte((short) 256)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- UShort ----

    @Test void testUShortRoundTrip() {
        assertRoundTrip(new AmqpType.UShort(0));
        assertRoundTrip(new AmqpType.UShort(32767));
        assertRoundTrip(new AmqpType.UShort(65535));
    }

    @Test void testUShortRangeValidation() {
        assertThatThrownBy(() -> new AmqpType.UShort(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AmqpType.UShort(65536)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- UInt ----

    @Test void testUIntZeroEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.UInt(0));
        assertThat(buf.remaining()).isEqualTo(1); // uint0 compact encoding
        assertThat(buf.get()).isEqualTo(TypeCodec.UINT_ZERO);
    }

    @Test void testUIntSmallEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.UInt(200));
        assertThat(buf.remaining()).isEqualTo(2); // smalluint encoding
        assertThat(buf.get()).isEqualTo(TypeCodec.UINT_SMALL);
    }

    @Test void testUIntLargeEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.UInt(70000));
        assertThat(buf.remaining()).isEqualTo(5); // full uint encoding
        assertThat(buf.get()).isEqualTo(TypeCodec.UINT);
    }

    @Test void testUIntRoundTrip() {
        assertRoundTrip(new AmqpType.UInt(0));
        assertRoundTrip(new AmqpType.UInt(1));
        assertRoundTrip(new AmqpType.UInt(255));
        assertRoundTrip(new AmqpType.UInt(256));
        assertRoundTrip(new AmqpType.UInt(70000));
        assertRoundTrip(new AmqpType.UInt(0xFFFFFFFFL));
    }

    @Test void testUIntRangeValidation() {
        assertThatThrownBy(() -> new AmqpType.UInt(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AmqpType.UInt(0x100000000L)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ULong ----

    @Test void testULongZeroEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.ULong(0));
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get()).isEqualTo(TypeCodec.ULONG_ZERO);
    }

    @Test void testULongSmallEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.ULong(100));
        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(buf.get()).isEqualTo(TypeCodec.ULONG_SMALL);
    }

    @Test void testULongLargeEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.ULong(0x1234567890L));
        assertThat(buf.remaining()).isEqualTo(9);
    }

    @Test void testULongRoundTrip() {
        assertRoundTrip(new AmqpType.ULong(0));
        assertRoundTrip(new AmqpType.ULong(1));
        assertRoundTrip(new AmqpType.ULong(255));
        assertRoundTrip(new AmqpType.ULong(256));
        assertRoundTrip(new AmqpType.ULong(0x1234567890L));
        assertRoundTrip(new AmqpType.ULong(Long.MAX_VALUE));
    }

    // ---- Byte ----

    @Test void testByteRoundTrip() {
        assertRoundTrip(new AmqpType.Byte((byte) 0));
        assertRoundTrip(new AmqpType.Byte((byte) -128));
        assertRoundTrip(new AmqpType.Byte((byte) 127));
    }

    // ---- Short ----

    @Test void testShortRoundTrip() {
        assertRoundTrip(new AmqpType.Short((short) 0));
        assertRoundTrip(new AmqpType.Short((short) -32768));
        assertRoundTrip(new AmqpType.Short((short) 32767));
    }

    // ---- Int ----

    @Test void testIntSmallEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Int(42));
        assertThat(buf.remaining()).isEqualTo(2); // smallint
        assertThat(buf.get()).isEqualTo(TypeCodec.INT_SMALL);
    }

    @Test void testIntLargeEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Int(1000));
        assertThat(buf.remaining()).isEqualTo(5); // full int
    }

    @Test void testIntRoundTrip() {
        assertRoundTrip(new AmqpType.Int(0));
        assertRoundTrip(new AmqpType.Int(-128));
        assertRoundTrip(new AmqpType.Int(127));
        assertRoundTrip(new AmqpType.Int(-129));
        assertRoundTrip(new AmqpType.Int(128));
        assertRoundTrip(new AmqpType.Int(Integer.MIN_VALUE));
        assertRoundTrip(new AmqpType.Int(Integer.MAX_VALUE));
    }

    // ---- Long ----

    @Test void testLongSmallEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Long(42));
        assertThat(buf.remaining()).isEqualTo(2);
        assertThat(buf.get()).isEqualTo(TypeCodec.LONG_SMALL);
    }

    @Test void testLongLargeEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.Long(1000));
        assertThat(buf.remaining()).isEqualTo(9);
    }

    @Test void testLongRoundTrip() {
        assertRoundTrip(new AmqpType.Long(0));
        assertRoundTrip(new AmqpType.Long(-128));
        assertRoundTrip(new AmqpType.Long(127));
        assertRoundTrip(new AmqpType.Long(-129));
        assertRoundTrip(new AmqpType.Long(128));
        assertRoundTrip(new AmqpType.Long(Long.MIN_VALUE));
        assertRoundTrip(new AmqpType.Long(Long.MAX_VALUE));
    }

    // ---- Float ----

    @Test void testFloatRoundTrip() {
        assertRoundTrip(new AmqpType.Float(0.0f));
        assertRoundTrip(new AmqpType.Float(3.14f));
        assertRoundTrip(new AmqpType.Float(-1.5f));
        assertRoundTrip(new AmqpType.Float(Float.MAX_VALUE));
        assertRoundTrip(new AmqpType.Float(Float.MIN_VALUE));
    }

    // ---- Double ----

    @Test void testDoubleRoundTrip() {
        assertRoundTrip(new AmqpType.Double(0.0));
        assertRoundTrip(new AmqpType.Double(3.14159265358979));
        assertRoundTrip(new AmqpType.Double(-1.5));
        assertRoundTrip(new AmqpType.Double(Double.MAX_VALUE));
    }

    // ---- Char ----

    @Test void testCharRoundTrip() {
        assertRoundTrip(new AmqpType.Char('A'));
        assertRoundTrip(new AmqpType.Char(0x1F600)); // Emoji
    }

    // ---- Timestamp ----

    @Test void testTimestampRoundTrip() {
        assertRoundTrip(new AmqpType.Timestamp(0));
        assertRoundTrip(new AmqpType.Timestamp(System.currentTimeMillis()));
        assertRoundTrip(new AmqpType.Timestamp(Long.MAX_VALUE));
    }

    // ---- UUID ----

    @Test void testUuidRoundTrip() {
        assertRoundTrip(new AmqpType.Uuid(UUID.randomUUID()));
        assertRoundTrip(new AmqpType.Uuid(new UUID(0, 0)));
        assertRoundTrip(new AmqpType.Uuid(new UUID(-1, -1)));
    }

    // ---- Binary ----

    @Test void testBinarySmallRoundTrip() {
        byte[] data = new byte[]{1, 2, 3, 4, 5};
        var original = new AmqpType.Binary(data);
        ByteBuffer buf = TypeCodec.encode(original);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Binary.class);
        assertThat(((AmqpType.Binary) decoded).value()).isEqualTo(data);
    }

    @Test void testBinaryEmptyRoundTrip() {
        var original = new AmqpType.Binary(new byte[0]);
        ByteBuffer buf = TypeCodec.encode(original);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Binary.class);
        assertThat(((AmqpType.Binary) decoded).value()).isEmpty();
    }

    @Test void testBinaryLargeRoundTrip() {
        byte[] data = new byte[300]; // > 255 triggers large encoding
        Arrays.fill(data, (byte) 42);
        var original = new AmqpType.Binary(data);
        ByteBuffer buf = TypeCodec.encode(original);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Binary.class);
        assertThat(((AmqpType.Binary) decoded).value()).isEqualTo(data);
    }

    // ---- String ----

    @Test void testStringSmallRoundTrip() {
        assertRoundTrip(new AmqpType.AmqpString("Hello, AMQP!"));
    }

    @Test void testStringEmptyRoundTrip() {
        assertRoundTrip(new AmqpType.AmqpString(""));
    }

    @Test void testStringUnicodeRoundTrip() {
        assertRoundTrip(new AmqpType.AmqpString("Hello, 世界!")); // Unicode
    }

    @Test void testStringLargeRoundTrip() {
        String large = "x".repeat(300);
        assertRoundTrip(new AmqpType.AmqpString(large));
    }

    // ---- Symbol ----

    @Test void testSymbolRoundTrip() {
        assertRoundTrip(new AmqpType.Symbol("amqp:accepted:list"));
    }

    @Test void testSymbolEmptyRoundTrip() {
        assertRoundTrip(new AmqpType.Symbol(""));
    }

    // ---- List ----

    @Test void testEmptyListRoundTrip() {
        assertRoundTrip(new AmqpType.AmqpList(List.of()));
    }

    @Test void testEmptyListEncoding() {
        ByteBuffer buf = TypeCodec.encode(new AmqpType.AmqpList(List.of()));
        assertThat(buf.remaining()).isEqualTo(1);
        assertThat(buf.get()).isEqualTo(TypeCodec.LIST_ZERO);
    }

    @Test void testListWithPrimitivesRoundTrip() {
        var list = new AmqpType.AmqpList(List.of(
                new AmqpType.Int(42),
                new AmqpType.AmqpString("hello"),
                new AmqpType.Bool(true),
                new AmqpType.Null()
        ));
        ByteBuffer buf = TypeCodec.encode(list);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpList.class);
        var decodedList = (AmqpType.AmqpList) decoded;
        assertThat(decodedList.elements()).hasSize(4);
        assertThat(decodedList.elements().get(0)).isEqualTo(new AmqpType.Int(42));
        assertThat(decodedList.elements().get(1)).isEqualTo(new AmqpType.AmqpString("hello"));
        assertThat(decodedList.elements().get(2)).isEqualTo(new AmqpType.Bool(true));
        assertThat(decodedList.elements().get(3)).isInstanceOf(AmqpType.Null.class);
    }

    @Test void testNestedListRoundTrip() {
        var inner = new AmqpType.AmqpList(List.of(new AmqpType.Int(1), new AmqpType.Int(2)));
        var outer = new AmqpType.AmqpList(List.of(inner, new AmqpType.AmqpString("outer")));
        ByteBuffer buf = TypeCodec.encode(outer);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpList.class);
        var decodedList = (AmqpType.AmqpList) decoded;
        assertThat(decodedList.elements()).hasSize(2);
        assertThat(decodedList.elements().get(0)).isInstanceOf(AmqpType.AmqpList.class);
    }

    // ---- Map ----

    @Test void testEmptyMapRoundTrip() {
        var map = new AmqpType.AmqpMap(Map.of());
        ByteBuffer buf = TypeCodec.encode(map);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpMap.class);
        assertThat(((AmqpType.AmqpMap) decoded).entries()).isEmpty();
    }

    @Test void testMapWithEntriesRoundTrip() {
        var entries = new LinkedHashMap<AmqpType, AmqpType>();
        entries.put(new AmqpType.Symbol("key1"), new AmqpType.AmqpString("value1"));
        entries.put(new AmqpType.Symbol("key2"), new AmqpType.Int(42));
        var map = new AmqpType.AmqpMap(entries);
        ByteBuffer buf = TypeCodec.encode(map);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpMap.class);
        var decodedMap = (AmqpType.AmqpMap) decoded;
        assertThat(decodedMap.entries()).hasSize(2);
    }

    // ---- Array ----

    @Test void testEmptyArrayRoundTrip() {
        var arr = new AmqpType.AmqpArray(List.of());
        ByteBuffer buf = TypeCodec.encode(arr);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpArray.class);
        assertThat(((AmqpType.AmqpArray) decoded).elements()).isEmpty();
    }

    @Test void testIntArrayRoundTrip() {
        var arr = new AmqpType.AmqpArray(List.of(
                new AmqpType.Int(10),
                new AmqpType.Int(20),
                new AmqpType.Int(30)
        ));
        ByteBuffer buf = TypeCodec.encode(arr);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpArray.class);
        var decodedArr = (AmqpType.AmqpArray) decoded;
        assertThat(decodedArr.elements()).hasSize(3);
        assertThat(decodedArr.elements().get(0)).isEqualTo(new AmqpType.Int(10));
        assertThat(decodedArr.elements().get(1)).isEqualTo(new AmqpType.Int(20));
        assertThat(decodedArr.elements().get(2)).isEqualTo(new AmqpType.Int(30));
    }

    @Test void testSymbolArrayRoundTrip() {
        var arr = new AmqpType.AmqpArray(List.of(
                new AmqpType.Symbol("PLAIN"),
                new AmqpType.Symbol("ANONYMOUS")
        ));
        ByteBuffer buf = TypeCodec.encode(arr);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpArray.class);
        var decodedArr = (AmqpType.AmqpArray) decoded;
        assertThat(decodedArr.elements()).hasSize(2);
        assertThat(decodedArr.elements().get(0)).isEqualTo(new AmqpType.Symbol("PLAIN"));
    }

    // ---- Described ----

    @Test void testDescribedTypeRoundTrip() {
        var described = new AmqpType.Described(
                new AmqpType.ULong(0x10), // open descriptor
                new AmqpType.AmqpList(List.of(new AmqpType.AmqpString("container-1")))
        );
        ByteBuffer buf = TypeCodec.encode(described);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Described.class);
        var decodedDesc = (AmqpType.Described) decoded;
        assertThat(TypeCodec.toLong(decodedDesc.descriptor())).isEqualTo(0x10);
        assertThat(decodedDesc.described()).isInstanceOf(AmqpType.AmqpList.class);
    }

    @Test void testDescribedWithSymbolDescriptor() {
        var described = new AmqpType.Described(
                new AmqpType.Symbol("amqp:my-type"),
                new AmqpType.AmqpString("value")
        );
        ByteBuffer buf = TypeCodec.encode(described);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Described.class);
        var decodedDesc = (AmqpType.Described) decoded;
        assertThat(TypeCodec.toString(decodedDesc.descriptor())).isEqualTo("amqp:my-type");
    }

    // ---- Utility methods ----

    @Test void testToStringFromString() {
        assertThat(TypeCodec.toString(new AmqpType.AmqpString("hello"))).isEqualTo("hello");
    }

    @Test void testToStringFromSymbol() {
        assertThat(TypeCodec.toString(new AmqpType.Symbol("sym"))).isEqualTo("sym");
    }

    @Test void testToStringFromInvalidType() {
        assertThatThrownBy(() -> TypeCodec.toString(new AmqpType.Int(42)))
                .isInstanceOf(AmqpException.class);
    }

    @Test void testToLongFromVariousTypes() {
        assertThat(TypeCodec.toLong(new AmqpType.UByte((short) 42))).isEqualTo(42);
        assertThat(TypeCodec.toLong(new AmqpType.UShort(1000))).isEqualTo(1000);
        assertThat(TypeCodec.toLong(new AmqpType.UInt(100000))).isEqualTo(100000);
        assertThat(TypeCodec.toLong(new AmqpType.ULong(999999999L))).isEqualTo(999999999L);
        assertThat(TypeCodec.toLong(new AmqpType.Byte((byte) -1))).isEqualTo(-1);
        assertThat(TypeCodec.toLong(new AmqpType.Short((short) -100))).isEqualTo(-100);
        assertThat(TypeCodec.toLong(new AmqpType.Int(-50000))).isEqualTo(-50000);
        assertThat(TypeCodec.toLong(new AmqpType.Long(-999999999L))).isEqualTo(-999999999L);
    }

    @Test void testToBooleanFromBool() {
        assertThat(TypeCodec.toBoolean(new AmqpType.Bool(true))).isTrue();
        assertThat(TypeCodec.toBoolean(new AmqpType.Bool(false))).isFalse();
    }

    @Test void testGetFieldInBounds() {
        var list = new AmqpType.AmqpList(List.of(
                new AmqpType.Int(1),
                new AmqpType.Null(),
                new AmqpType.AmqpString("test")
        ));
        assertThat(TypeCodec.getField(list, 0)).isEqualTo(new AmqpType.Int(1));
        assertThat(TypeCodec.getField(list, 1)).isNull(); // null maps to null
        assertThat(TypeCodec.getField(list, 2)).isEqualTo(new AmqpType.AmqpString("test"));
    }

    @Test void testGetFieldOutOfBounds() {
        var list = new AmqpType.AmqpList(List.of(new AmqpType.Int(1)));
        assertThat(TypeCodec.getField(list, 5)).isNull();
    }

    @Test void testUnknownTypeCode() {
        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte) 0xFF);
        buf.flip();
        assertThatThrownBy(() -> TypeCodec.decode(buf))
                .isInstanceOf(AmqpException.class)
                .hasMessageContaining("Unknown type code");
    }

    @Test void testEstimateSize() {
        assertThat(TypeCodec.estimateSize(new AmqpType.Null())).isEqualTo(1);
        assertThat(TypeCodec.estimateSize(new AmqpType.Bool(true))).isEqualTo(1);
        assertThat(TypeCodec.estimateSize(new AmqpType.UByte((short) 42))).isEqualTo(2);
        assertThat(TypeCodec.estimateSize(new AmqpType.Int(42))).isEqualTo(5);
        assertThat(TypeCodec.estimateSize(new AmqpType.AmqpString("hi"))).isGreaterThanOrEqualTo(4);
    }

    @Test void testConstructorByteFor() {
        assertThat(TypeCodec.constructorByteFor(new AmqpType.Null())).isEqualTo(TypeCodec.NULL);
        assertThat(TypeCodec.constructorByteFor(new AmqpType.Bool(true))).isEqualTo(TypeCodec.BOOLEAN);
        assertThat(TypeCodec.constructorByteFor(new AmqpType.UByte((short) 1))).isEqualTo(TypeCodec.UBYTE);
        assertThat(TypeCodec.constructorByteFor(new AmqpType.Int(1))).isEqualTo(TypeCodec.INT);
    }

    // ---- Multiple values in sequence ----

    @Test void testMultipleValuesInBuffer() {
        var buf = ByteBuffer.allocate(100);
        TypeCodec.encodeInto(new AmqpType.Int(42), buf);
        TypeCodec.encodeInto(new AmqpType.AmqpString("hello"), buf);
        TypeCodec.encodeInto(new AmqpType.Bool(true), buf);
        buf.flip();

        assertThat(TypeCodec.decode(buf)).isEqualTo(new AmqpType.Int(42));
        assertThat(TypeCodec.decode(buf)).isEqualTo(new AmqpType.AmqpString("hello"));
        assertThat(TypeCodec.decode(buf)).isEqualTo(new AmqpType.Bool(true));
    }

    @Test void testBufferUnderflow() {
        ByteBuffer buf = ByteBuffer.allocate(0);
        assertThatThrownBy(() -> TypeCodec.decode(buf))
                .isInstanceOf(AmqpException.class);
    }

    // ---- Helper ----

    private void assertRoundTrip(AmqpType original) {
        ByteBuffer buf = TypeCodec.encode(original);
        buf.rewind();
        AmqpType decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }
}
