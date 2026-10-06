package ssg.legoflow.messaging.amqp.types;

import org.junit.jupiter.api.Test;
import ssg.legoflow.messaging.amqp.common.AmqpException;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Coverage for AMQP type system: AmqpType sealed hierarchy + TypeCodec encode/decode. */
class AmqpTypesTest {

    @Test
    void descriptorConstants() {
        assertEquals(0x10L, Descriptors.OPEN);
        assertEquals(0x11L, Descriptors.BEGIN);
        assertEquals(0x12L, Descriptors.ATTACH);
        assertEquals(0x14L, Descriptors.TRANSFER);
        assertEquals(0x15L, Descriptors.DISPOSITION);
        assertEquals(0x40L, Descriptors.SASL_MECHANISMS);
        assertEquals(0x41L, Descriptors.SASL_INIT);
        assertEquals(0x70L, Descriptors.HEADER);
        assertEquals(0x75L, Descriptors.DATA);
        assertEquals(0x24L, Descriptors.ACCEPTED);
        assertEquals(0x28L, Descriptors.SOURCE);
        assertEquals(0x1DL, Descriptors.ERROR);
    }

    @Test
    void nullRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Null(), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Null);
    }

    @Test
    void boolTrueRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Bool(true), buf);
        buf.flip();
        var d = TypeCodec.decode(buf);
        assertTrue(d instanceof AmqpType.Bool b && b.value());
    }

    @Test
    void boolFalseRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Bool(false), buf);
        buf.flip();
        var d = TypeCodec.decode(buf);
        assertTrue(d instanceof AmqpType.Bool b && !b.value());
    }

    @Test
    void ubyteRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.UByte((short) 42), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.UByte ub && ub.value() == 42);
    }

    @Test
    void ushortRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.UShort(1000), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.UShort us && us.value() == 1000);
    }

    @Test
    void uintZeroRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.UInt(0), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.UInt ui && ui.value() == 0);
    }

    @Test
    void uintSmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.UInt(200), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.UInt ui && ui.value() == 200);
    }

    @Test
    void uintLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.UInt(50000), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.UInt ui && ui.value() == 50000);
    }

    @Test
    void ulongZeroRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.ULong(0), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.ULong ul && ul.value() == 0);
    }

    @Test
    void ulongSmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.ULong(100), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.ULong ul && ul.value() == 100);
    }

    @Test
    void ulongLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.ULong(9999999999L), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.ULong ul && ul.value() == 9999999999L);
    }

    @Test
    void byteRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Byte((byte) -42), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Byte b && b.value() == -42);
    }

    @Test
    void shortRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Short((short) -1000), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Short s && s.value() == -1000);
    }

    @Test
    void intSmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Int(100), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Int i && i.value() == 100);
    }

    @Test
    void intLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Int(-50000), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Int i && i.value() == -50000);
    }

    @Test
    void longSmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Long(100), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Long l && l.value() == 100);
    }

    @Test
    void longLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Long(-50000000L), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Long l && l.value() == -50000000L);
    }

    @Test
    void floatRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Float(2.71f), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Float f && Math.abs(f.value() - 2.71f) < 0.001f);
    }

    @Test
    void doubleRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Double(3.14159), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Double d && Math.abs(d.value() - 3.14159) < 0.00001);
    }

    @Test
    void charRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        TypeCodec.encodeInto(new AmqpType.Char(0x41), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Char c && c.codePoint() == 0x41);
    }

    @Test
    void timestampRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        long ts = 1700000000000L;
        TypeCodec.encodeInto(new AmqpType.Timestamp(ts), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Timestamp t && t.millis() == ts);
    }

    @Test
    void uuidRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(32);
        var uuid = new java.util.UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
        TypeCodec.encodeInto(new AmqpType.Uuid(uuid), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Uuid u && u.value().equals(uuid));
    }

    @Test
    void binarySmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        byte[] data = {1, 2, 3, 4};
        TypeCodec.encodeInto(new AmqpType.Binary(data), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Binary b && java.util.Arrays.equals(b.value(), data));
    }

    @Test
    void binaryLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        byte[] data = new byte[300];
        data[0] = 42;
        TypeCodec.encodeInto(new AmqpType.Binary(data), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Binary b && java.util.Arrays.equals(b.value(), data));
    }

    @Test
    void stringSmallRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        TypeCodec.encodeInto(new AmqpType.AmqpString("hello"), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.AmqpString s && s.value().equals("hello"));
    }

    @Test
    void stringLargeRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(4096);
        String longStr = "x".repeat(300);
        TypeCodec.encodeInto(new AmqpType.AmqpString(longStr), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.AmqpString s && s.value().equals(longStr));
    }

    @Test
    void symbolRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        TypeCodec.encodeInto(new AmqpType.Symbol("amqp:ok"), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Symbol sym && sym.value().equals("amqp:ok"));
    }

    @Test
    void emptyListRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        TypeCodec.encodeInto(new AmqpType.AmqpList(List.of()), buf);
        buf.flip();
        var d = TypeCodec.decode(buf);
        assertTrue(d instanceof AmqpType.AmqpList list && list.elements().isEmpty());
    }

    @Test
    void listWithElementsRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        TypeCodec.encodeInto(new AmqpType.AmqpList(List.of(
            new AmqpType.AmqpString("a"),
            new AmqpType.UInt(1)
        )), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.AmqpList list && list.elements().size() == 2);
    }

    @Test
    void emptyMapRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        TypeCodec.encodeInto(new AmqpType.AmqpMap(Map.of()), buf);
        buf.flip();
        var d = TypeCodec.decode(buf);
        assertTrue(d instanceof AmqpType.AmqpMap map && map.entries().isEmpty());
    }

    @Test
    void mapRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        Map<AmqpType, AmqpType> entries = new HashMap<>();
        entries.put(new AmqpType.Symbol("key"), new AmqpType.AmqpString("value"));
        TypeCodec.encodeInto(new AmqpType.AmqpMap(entries), buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.AmqpMap map && !map.entries().isEmpty());
    }

    @Test
    void describedRoundTrip() {
        ByteBuffer buf = ByteBuffer.allocate(256);
        var described = new AmqpType.Described(
            new AmqpType.ULong(Descriptors.SASL_OUTCOME),
            new AmqpType.AmqpList(List.of(new AmqpType.UByte((short) 0)))
        );
        TypeCodec.encodeInto(described, buf);
        buf.flip();
        assertTrue(TypeCodec.decode(buf) instanceof AmqpType.Described);
    }

    @Test
    void encodeReturnsFlippedBuffer() {
        var buf = TypeCodec.encode(new AmqpType.AmqpString("hi"));
        assertTrue(buf.hasRemaining());
    }

    @Test
    void encodeDecodeSymmetry() {
        var original = new AmqpType.Double(1.23);
        var buf = TypeCodec.encode(original);
        var decoded = TypeCodec.decode(buf);
        assertEquals(original, decoded);
    }

    @Test
    void ubyteOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new AmqpType.UByte((short) 256));
    }

    @Test
    void ushortOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new AmqpType.UShort(-1));
    }

    @Test
    void uintOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new AmqpType.UInt(-1));
    }

    @Test
    void descriptorValuesAreUnique() {
        var all = new java.util.HashSet<Long>();
        all.add(Descriptors.OPEN);
        all.add(Descriptors.BEGIN);
        all.add(Descriptors.ATTACH);
        all.add(Descriptors.FLOW);
        all.add(Descriptors.TRANSFER);
        all.add(Descriptors.DISPOSITION);
        all.add(Descriptors.DETACH);
        all.add(Descriptors.END);
        all.add(Descriptors.CLOSE);
        all.add(Descriptors.SASL_MECHANISMS);
        all.add(Descriptors.SASL_INIT);
        all.add(Descriptors.SASL_CHALLENGE);
        all.add(Descriptors.SASL_RESPONSE);
        all.add(Descriptors.SASL_OUTCOME);
        all.add(Descriptors.HEADER);
        all.add(Descriptors.DATA);
        all.add(Descriptors.AMQP_VALUE);
        all.add(Descriptors.ACCEPTED);
        all.add(Descriptors.REJECTED);
        all.add(Descriptors.RELEASED);
        all.add(Descriptors.SOURCE);
        all.add(Descriptors.TARGET);
        all.add(Descriptors.ERROR);
        assertEquals(23, all.size());
    }

    @Test
    void typeCodes() {
        assertEquals(0x40, TypeCodec.NULL);
        assertEquals(0x41, TypeCodec.BOOLEAN_TRUE);
        assertEquals(0x42, TypeCodec.BOOLEAN_FALSE);
        assertEquals(0x45, TypeCodec.LIST_ZERO);
    }

    @Test
    void decodeEmptyBufferThrows() {
        var buf = ByteBuffer.allocate(0);
        assertThrows(AmqpException.class, () -> TypeCodec.decode(buf));
    }

    @Test
    void constructorByteFor() {
        assertEquals(TypeCodec.NULL, TypeCodec.constructorByteFor(new AmqpType.Null()));
        assertEquals(TypeCodec.BOOLEAN, TypeCodec.constructorByteFor(new AmqpType.Bool(true)));
        assertEquals(TypeCodec.UBYTE, TypeCodec.constructorByteFor(new AmqpType.UByte((short) 1)));
        assertEquals(TypeCodec.STRING_SMALL, TypeCodec.constructorByteFor(new AmqpType.AmqpString("hi")));
        assertEquals(TypeCodec.LIST_ZERO, TypeCodec.constructorByteFor(new AmqpType.AmqpList(List.of())));
    }
}
