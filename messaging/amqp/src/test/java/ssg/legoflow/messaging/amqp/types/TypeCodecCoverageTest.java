package ssg.legoflow.messaging.amqp.types;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Additional TypeCodec coverage for types not tested in TypeCodecTest.
 */
class TypeCodecCoverageTest {

    @Test void uByteRoundTrip() {
        var original = new AmqpType.UByte((short) 255);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void uShortRoundTrip() {
        var original = new AmqpType.UShort(65535);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void byteRoundTrip() {
        var original = new AmqpType.Byte((byte) -128);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void shortRoundTrip() {
        var original = new AmqpType.Short((short) -32768);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void floatRoundTrip() {
        var original = new AmqpType.Float(3.14f);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void doubleRoundTrip() {
        var original = new AmqpType.Double(2.7182818284857);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void charRoundTrip() {
        var original = new AmqpType.Char('A');
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void timestampRoundTrip() {
        var original = new AmqpType.Timestamp(1700000000000L);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void uuidRoundTrip() {
        var id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        var original = new AmqpType.Uuid(id);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void binaryRoundTrip() {
        var original = new AmqpType.Binary(new byte[]{1, 2, 3, 4, 5});
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Binary.class);
        var decodedBinary = (AmqpType.Binary) decoded;
        assertThat(decodedBinary.value()).containsExactly(1, 2, 3, 4, 5);
    }

    @Test void symbolRoundTrip() {
        var original = new AmqpType.Symbol("com.example:symbol");
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void amqpMapRoundTrip() {
        var map = new java.util.HashMap<AmqpType, AmqpType>();
        map.put(new AmqpType.AmqpString("key1"), new AmqpType.AmqpString("val1"));
        map.put(new AmqpType.AmqpString("key2"), new AmqpType.Int(42));
        var original = new AmqpType.AmqpMap(map);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        // Maps may reorder, so check size and keys
        assertThat(decoded).isInstanceOf(AmqpType.AmqpMap.class);
        var decodedMap = (AmqpType.AmqpMap) decoded;
        assertThat(decodedMap.entries()).hasSize(2);
        assertThat(decodedMap.entries()).containsKey(new AmqpType.AmqpString("key1"));
        assertThat(decodedMap.entries()).containsKey(new AmqpType.AmqpString("key2"));
    }

    @Test void amqpArrayRoundTrip() {
        var original = new AmqpType.AmqpArray(List.of(
            new AmqpType.Int(1),
            new AmqpType.Int(2),
            new AmqpType.Int(3)
        ));
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void describedRoundTrip() {
        var descriptor = new AmqpType.ULong(0x000000000000020L); // amqp:accepted:list;no-properties
        var described = new AmqpType.Described(descriptor, new AmqpType.Null());
        var buf = TypeCodec.encode(described);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Described.class);
    }

    @Test void emptyBinary() {
        var original = new AmqpType.Binary(new byte[0]);
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.Binary.class);
        var decodedBinary = (AmqpType.Binary) decoded;
        assertThat(decodedBinary.value()).isEmpty();
    }

    @Test void emptySymbol() {
        var original = new AmqpType.Symbol("");
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void emptyArray() {
        var original = new AmqpType.AmqpArray(List.of());
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isEqualTo(original);
    }

    @Test void emptyMap() {
        var original = new AmqpType.AmqpMap(Map.of());
        var buf = TypeCodec.encode(original);
        buf.rewind();
        var decoded = TypeCodec.decode(buf);
        assertThat(decoded).isInstanceOf(AmqpType.AmqpMap.class);
    }
}
