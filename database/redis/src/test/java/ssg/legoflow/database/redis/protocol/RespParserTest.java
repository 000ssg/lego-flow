package ssg.legoflow.database.redis.protocol;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RespParser} — streaming parser correctness and edge cases.
 */
class RespParserTest {

    @Test
    void testParseInlineCommand() throws IOException {
        var parser = parser("PING\r\n");
        var result = parser.parse();
        assertThat(result).isInstanceOf(RespType.Array.class);
        var arr = (RespType.Array) result;
        assertThat(arr.elements()).hasSize(1);
        assertThat(((RespType.BulkString) arr.elements().get(0)).asString()).isEqualTo("PING");
    }

    @Test
    void testParseInlineCommandWithArgs() throws IOException {
        var parser = parser("SET key value\r\n");
        var result = (RespType.Array) parser.parse();
        assertThat(result.elements()).hasSize(3);
    }

    @Test
    void testParseMultipleMessages() throws IOException {
        String data = "+OK\r\n:42\r\n$3\r\nfoo\r\n";
        var parser = parser(data);

        var first = parser.parse();
        assertThat(first).isInstanceOf(RespType.SimpleString.class);
        assertThat(((RespType.SimpleString) first).value()).isEqualTo("OK");

        var second = parser.parse();
        assertThat(second).isInstanceOf(RespType.Integer.class);
        assertThat(((RespType.Integer) second).value()).isEqualTo(42);

        var third = parser.parse();
        assertThat(third).isInstanceOf(RespType.BulkString.class);
        assertThat(((RespType.BulkString) third).asString()).isEqualTo("foo");
    }

    @Test
    void testParseEndOfStream() throws IOException {
        var parser = parser("");
        assertThat(parser.parse()).isNull();
    }

    @Test
    void testParseErrorPrefix() throws IOException {
        var parser = parser("-WRONGTYPE Operation against wrong type\r\n");
        var result = (RespType.Error) parser.parse();
        assertThat(result.prefix()).isEqualTo("WRONGTYPE");
        assertThat(result.message()).isEqualTo("Operation against wrong type");
    }

    @Test
    void testParseNullBulkString() throws IOException {
        var parser = parser("$-1\r\n");
        var result = (RespType.BulkString) parser.parse();
        assertThat(result.value()).isNull();
    }

    @Test
    void testParseNullArray() throws IOException {
        var parser = parser("*-1\r\n");
        var result = (RespType.Array) parser.parse();
        assertThat(result.elements()).isNull();
    }

    @Test
    void testParseBulkStringWithCRLF() throws IOException {
        // Bulk string containing \r\n
        String data = "$5\r\nhe\r\no\r\n";
        var parser = parser(data);
        var result = (RespType.BulkString) parser.parse();
        assertThat(result.asString()).isEqualTo("he\r\no");
    }

    @Test
    void testParseComplexArray() throws IOException {
        // *3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n
        String data = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n";
        var parser = parser(data);
        var result = (RespType.Array) parser.parse();
        assertThat(result.elements()).hasSize(3);
        assertThat(((RespType.BulkString) result.elements().get(0)).asString()).isEqualTo("SET");
        assertThat(((RespType.BulkString) result.elements().get(1)).asString()).isEqualTo("key");
        assertThat(((RespType.BulkString) result.elements().get(2)).asString()).isEqualTo("value");
    }

    @Test
    void testParseResp3Null() throws IOException {
        var parser = parser("_\r\n");
        var result = parser.parse();
        assertThat(result).isInstanceOf(RespType.Null.class);
    }

    @Test
    void testParseResp3Double() throws IOException {
        var parser = parser(",3.14\r\n");
        var result = (RespType.RespDouble) parser.parse();
        assertThat(result.value()).isEqualTo(3.14);
    }

    @Test
    void testParseResp3Boolean() throws IOException {
        var parser = parser("#t\r\n#f\r\n");
        assertThat(((RespType.RespBoolean) parser.parse()).value()).isTrue();
        assertThat(((RespType.RespBoolean) parser.parse()).value()).isFalse();
    }

    @Test
    void testParseResp3Map() throws IOException {
        String data = "%2\r\n+first\r\n:1\r\n+second\r\n:2\r\n";
        var parser = parser(data);
        var result = (RespType.RespMap) parser.parse();
        assertThat(result.entries()).hasSize(2);
    }

    @Test
    void testParseResp3Set() throws IOException {
        String data = "~2\r\n+orange\r\n+apple\r\n";
        var parser = parser(data);
        var result = (RespType.RespSet) parser.parse();
        assertThat(result.elements()).hasSize(2);
    }

    @Test
    void testParseResp3Push() throws IOException {
        String data = ">3\r\n+subscribe\r\n+channel\r\n:1\r\n";
        var parser = parser(data);
        var result = (RespType.Push) parser.parse();
        assertThat(result.elements()).hasSize(3);
    }

    @Test
    void testParseResp3Attribute() throws IOException {
        String data = "|1\r\n+key\r\n+value\r\n";
        var parser = parser(data);
        var result = (RespType.Attribute) parser.parse();
        assertThat(result.attributes()).hasSize(1);
    }

    @Test
    void testParseResp3BigNumber() throws IOException {
        var parser = parser("(12345678901234567890\r\n");
        var result = (RespType.BigNumber) parser.parse();
        assertThat(result.value().toString()).isEqualTo("12345678901234567890");
    }

    @Test
    void testParseResp3VerbatimString() throws IOException {
        String data = "=15\r\ntxt:Some string\r\n";
        var parser = parser(data);
        var result = (RespType.VerbatimString) parser.parse();
        assertThat(result.encoding()).isEqualTo("txt");
        assertThat(result.value()).isEqualTo("Some string");
    }

    @Test
    void testParsePipelinedCommands() throws IOException {
        // Multiple commands in sequence (pipelining)
        String data = "*1\r\n$4\r\nPING\r\n*2\r\n$4\r\nECHO\r\n$5\r\nhello\r\n";
        var parser = parser(data);

        var cmd1 = (RespType.Array) parser.parse();
        assertThat(cmd1.elements()).hasSize(1);

        var cmd2 = (RespType.Array) parser.parse();
        assertThat(cmd2.elements()).hasSize(2);
    }

    private RespParser parser(String data) {
        return new RespParser(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)));
    }
}
