package ssg.legoflow.database.redis.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link RespCodec} — encode/decode round-trip for all RESP2 and RESP3 types.
 */
class RespCodecTest {

    // ---- RESP2 types ----

    @Test
    void testSimpleStringRoundTrip() throws IOException {
        var original = new RespType.SimpleString("OK");
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.SimpleString.class);
        assertThat(((RespType.SimpleString) decoded).value()).isEqualTo("OK");
    }

    @Test
    void testSimpleStringEmpty() throws IOException {
        var original = new RespType.SimpleString("");
        var decoded = roundTrip(original);
        assertThat(((RespType.SimpleString) decoded).value()).isEmpty();
    }

    @Test
    void testErrorRoundTrip() throws IOException {
        var original = new RespType.Error("ERR", "unknown command");
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Error.class);
        var err = (RespType.Error) decoded;
        assertThat(err.prefix()).isEqualTo("ERR");
        assertThat(err.message()).isEqualTo("unknown command");
    }

    @Test
    void testErrorWithWrongtype() throws IOException {
        var original = new RespType.Error("WRONGTYPE", "Operation against a key holding the wrong kind of value");
        var decoded = roundTrip(original);
        var err = (RespType.Error) decoded;
        assertThat(err.prefix()).isEqualTo("WRONGTYPE");
        assertThat(err.fullMessage()).startsWith("WRONGTYPE");
    }

    @Test
    void testIntegerRoundTrip() throws IOException {
        var original = new RespType.Integer(1000);
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) decoded).value()).isEqualTo(1000);
    }

    @Test
    void testIntegerNegative() throws IOException {
        var original = new RespType.Integer(-42);
        var decoded = roundTrip(original);
        assertThat(((RespType.Integer) decoded).value()).isEqualTo(-42);
    }

    @Test
    void testIntegerZero() throws IOException {
        var original = new RespType.Integer(0);
        var decoded = roundTrip(original);
        assertThat(((RespType.Integer) decoded).value()).isEqualTo(0);
    }

    @Test
    void testIntegerMaxValue() throws IOException {
        var original = new RespType.Integer(Long.MAX_VALUE);
        var decoded = roundTrip(original);
        assertThat(((RespType.Integer) decoded).value()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void testBulkStringRoundTrip() throws IOException {
        var original = RespType.BulkString.of("foobar");
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) decoded).asString()).isEqualTo("foobar");
    }

    @Test
    void testBulkStringEmpty() throws IOException {
        var original = RespType.BulkString.of("");
        var decoded = roundTrip(original);
        assertThat(((RespType.BulkString) decoded).asString()).isEmpty();
    }

    @Test
    void testBulkStringNull() throws IOException {
        var original = RespType.BulkString.NULL;
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("$-1\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) decoded).value()).isNull();
        assertThat(((RespType.BulkString) decoded).asString()).isNull();
    }

    @Test
    void testBulkStringBinary() throws IOException {
        byte[] binary = {0, 1, 2, (byte) 0xFF, '\r', '\n', 42};
        var original = new RespType.BulkString(binary);
        var decoded = roundTrip(original);
        assertThat(((RespType.BulkString) decoded).value()).isEqualTo(binary);
    }

    @Test
    void testArrayRoundTrip() throws IOException {
        var original = new RespType.Array(List.of(
                RespType.BulkString.of("SET"),
                RespType.BulkString.of("key"),
                RespType.BulkString.of("value")));
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Array.class);
        var arr = (RespType.Array) decoded;
        assertThat(arr.elements()).hasSize(3);
        assertThat(((RespType.BulkString) arr.elements().get(0)).asString()).isEqualTo("SET");
    }

    @Test
    void testArrayEmpty() throws IOException {
        var original = new RespType.Array(List.of());
        var decoded = roundTrip(original);
        assertThat(((RespType.Array) decoded).elements()).isEmpty();
    }

    @Test
    void testArrayNull() throws IOException {
        var original = RespType.Array.NULL;
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("*-1\r\n");
    }

    @Test
    void testArrayNested() throws IOException {
        var inner = new RespType.Array(List.of(new RespType.Integer(1), new RespType.Integer(2)));
        var original = new RespType.Array(List.of(inner, RespType.BulkString.of("hello")));
        var decoded = (RespType.Array) roundTrip(original);
        assertThat(decoded.elements()).hasSize(2);
        assertThat(decoded.elements().get(0)).isInstanceOf(RespType.Array.class);
    }

    @Test
    void testArrayMixedTypes() throws IOException {
        var original = new RespType.Array(List.of(
                new RespType.SimpleString("OK"),
                new RespType.Integer(42),
                RespType.BulkString.of("data"),
                RespType.BulkString.NULL));
        var decoded = (RespType.Array) roundTrip(original);
        assertThat(decoded.elements()).hasSize(4);
    }

    // ---- RESP3 types ----

    @Test
    void testNullRoundTrip() throws IOException {
        var original = RespType.Null.INSTANCE;
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("_\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Null.class);
    }

    @Test
    void testDoubleRoundTrip() throws IOException {
        var original = new RespType.RespDouble(1.23);
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.RespDouble.class);
        assertThat(((RespType.RespDouble) decoded).value()).isEqualTo(1.23);
    }

    @Test
    void testDoubleInfinity() throws IOException {
        var posInf = new RespType.RespDouble(Double.POSITIVE_INFINITY);
        var decoded = roundTrip(posInf);
        assertThat(((RespType.RespDouble) decoded).value()).isEqualTo(Double.POSITIVE_INFINITY);

        var negInf = new RespType.RespDouble(Double.NEGATIVE_INFINITY);
        decoded = roundTrip(negInf);
        assertThat(((RespType.RespDouble) decoded).value()).isEqualTo(Double.NEGATIVE_INFINITY);
    }

    @Test
    void testDoubleZero() throws IOException {
        var decoded = roundTrip(new RespType.RespDouble(0.0));
        assertThat(((RespType.RespDouble) decoded).value()).isEqualTo(0.0);
    }

    @Test
    void testBooleanTrue() throws IOException {
        var original = new RespType.RespBoolean(true);
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("#t\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.RespBoolean.class);
        assertThat(((RespType.RespBoolean) decoded).value()).isTrue();
    }

    @Test
    void testBooleanFalse() throws IOException {
        var original = new RespType.RespBoolean(false);
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("#f\r\n");

        var decoded = roundTrip(original);
        assertThat(((RespType.RespBoolean) decoded).value()).isFalse();
    }

    @Test
    void testBigNumberRoundTrip() throws IOException {
        BigInteger big = new BigInteger("3492890328409238509324850943850943825024385");
        var original = new RespType.BigNumber(big);
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.BigNumber.class);
        assertThat(((RespType.BigNumber) decoded).value()).isEqualTo(big);
    }

    @Test
    void testBlobErrorRoundTrip() throws IOException {
        var original = new RespType.BlobError("SYNTAX invalid syntax".getBytes(StandardCharsets.UTF_8));
        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("!21\r\nSYNTAX invalid syntax\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.BlobError.class);
        assertThat(((RespType.BlobError) decoded).asString()).isEqualTo("SYNTAX invalid syntax");
    }

    @Test
    void testVerbatimStringRoundTrip() throws IOException {
        var original = new RespType.VerbatimString("txt", "Some string");
        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.VerbatimString.class);
        var vs = (RespType.VerbatimString) decoded;
        assertThat(vs.encoding()).isEqualTo("txt");
        assertThat(vs.value()).isEqualTo("Some string");
    }

    @Test
    void testVerbatimStringMarkdown() throws IOException {
        var original = new RespType.VerbatimString("mkd", "# Title");
        var decoded = (RespType.VerbatimString) roundTrip(original);
        assertThat(decoded.encoding()).isEqualTo("mkd");
        assertThat(decoded.value()).isEqualTo("# Title");
    }

    @Test
    void testMapRoundTrip() throws IOException {
        Map<RespType, RespType> entries = new LinkedHashMap<>();
        entries.put(new RespType.SimpleString("first"), new RespType.Integer(1));
        entries.put(new RespType.SimpleString("second"), new RespType.Integer(2));
        var original = new RespType.RespMap(entries);

        byte[] encoded = RespCodec.encode(original);
        String wire = new String(encoded, StandardCharsets.UTF_8);
        assertThat(wire).startsWith("%2\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.RespMap.class);
        var map = (RespType.RespMap) decoded;
        assertThat(map.entries()).hasSize(2);
    }

    @Test
    void testSetRoundTrip() throws IOException {
        Set<RespType> elements = new LinkedHashSet<>();
        elements.add(new RespType.SimpleString("orange"));
        elements.add(new RespType.SimpleString("apple"));
        var original = new RespType.RespSet(elements);

        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).startsWith("~2\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.RespSet.class);
        assertThat(((RespType.RespSet) decoded).elements()).hasSize(2);
    }

    @Test
    void testAttributeRoundTrip() throws IOException {
        Map<RespType, RespType> attrs = new LinkedHashMap<>();
        attrs.put(new RespType.SimpleString("ttl"), new RespType.Integer(3600));
        var original = new RespType.Attribute(attrs);

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Attribute.class);
        assertThat(((RespType.Attribute) decoded).attributes()).hasSize(1);
    }

    @Test
    void testPushRoundTrip() throws IOException {
        var original = new RespType.Push(List.of(
                new RespType.SimpleString("subscribe"),
                new RespType.SimpleString("channel"),
                new RespType.Integer(1)));

        byte[] encoded = RespCodec.encode(original);
        assertThat(new String(encoded, StandardCharsets.UTF_8)).startsWith(">3\r\n");

        var decoded = roundTrip(original);
        assertThat(decoded).isInstanceOf(RespType.Push.class);
        assertThat(((RespType.Push) decoded).elements()).hasSize(3);
    }

    // ---- Command encoding ----

    @Test
    void testEncodeCommand() throws IOException {
        byte[] encoded = RespCodec.encodeCommand("SET", "key", "value");
        var decoded = parse(encoded);
        assertThat(decoded).isInstanceOf(RespType.Array.class);
        var arr = (RespType.Array) decoded;
        assertThat(arr.elements()).hasSize(3);
        assertThat(((RespType.BulkString) arr.elements().get(0)).asString()).isEqualTo("SET");
        assertThat(((RespType.BulkString) arr.elements().get(1)).asString()).isEqualTo("key");
        assertThat(((RespType.BulkString) arr.elements().get(2)).asString()).isEqualTo("value");
    }

    @Test
    void testEncodeInlineCommand() {
        byte[] encoded = RespCodec.encodeInlineCommand("PING");
        assertThat(new String(encoded, StandardCharsets.UTF_8)).isEqualTo("PING\r\n");
    }

    @Test
    void testEncodeCommandSingleArg() throws IOException {
        byte[] encoded = RespCodec.encodeCommand("PING");
        var decoded = (RespType.Array) parse(encoded);
        assertThat(decoded.elements()).hasSize(1);
    }

    // ---- Helpers ----

    private RespType roundTrip(RespType original) throws IOException {
        byte[] encoded = RespCodec.encode(original);
        return parse(encoded);
    }

    private RespType parse(byte[] data) throws IOException {
        return new RespParser(new ByteArrayInputStream(data)).parse();
    }
}
