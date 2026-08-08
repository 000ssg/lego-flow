package ssg.legoflow.email.imap.protocol;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class ImapLiteralTest {

    @Test void testOfString() {
        var lit = ImapLiteral.of("hello world");
        assertThat(lit.asString()).isEqualTo("hello world");
        assertThat(lit.size()).isEqualTo(11);
        assertThat(lit.isNonSynchronizing()).isFalse();
    }

    @Test void testNonSync() {
        var lit = ImapLiteral.nonSync("test data");
        assertThat(lit.asString()).isEqualTo("test data");
        assertThat(lit.size()).isEqualTo(9);
        assertThat(lit.isNonSynchronizing()).isTrue();
    }

    @Test void testConstructorWithData() {
        byte[] data = new byte[]{66, 105, 110}; // "Bin" 
        var lit = new ImapLiteral(data, false);
        assertThat(lit.data()).isEqualTo(data);
        assertThat(lit.size()).isEqualTo(data.length);
    }

    @Test void testConstructorRejectsNullData() {
        assertThatThrownBy(() -> new ImapLiteral(null, false))
                .isInstanceOf(NullPointerException.class);
    }

    @Test void testDataReturnsClone() {
        byte[] original = new byte[]{1, 2, 3};
        var lit = new ImapLiteral(original, false);
        byte[] copy = lit.data();
        assertThat(copy).isEqualTo(original);
        // Mutating the returned array shouldn't affect internal state
        copy[0] = 99;
        assertThat(lit.data()[0]).isNotEqualTo((byte)99);
    }

    @Test void testHeaderSynchronizing() {
        var lit = ImapLiteral.of("abc");
        assertThat(lit.header()).isEqualTo("{3}");
    }

    @Test void testHeaderNonSynchronizing() {
        var lit = ImapLiteral.nonSync("test");
        assertThat(lit.header()).isEqualTo("{4+}");
    }

    @Test void testHeaderEmptyData() {
        var lit = new ImapLiteral(new byte[0], false);
        assertThat(lit.header()).isEqualTo("{0}");
    }

    @Test void testAsString() {
        var lit = new ImapLiteral("hello".getBytes(StandardCharsets.UTF_8), false);
        assertThat(lit.asString()).isEqualTo("hello");
    }

    @Test void testParseLiteralHeaderSynchronizing() {
        long[] result = ImapLiteral.parseLiteralHeader("{123}");
        assertThat(result).containsExactly(123L, 0L);
    }

    @Test void testParseLiteralHeaderNonSynchronizing() {
        long[] result = ImapLiteral.parseLiteralHeader("{456+}");
        assertThat(result).containsExactly(456L, 1L);
    }

    @Test void testParseLiteralHeaderZeroSize() {
        long[] result = ImapLiteral.parseLiteralHeader("{0}");
        assertThat(result).containsExactly(0L, 0L);
    }

    @Test void testParseLiteralHeaderInvalidNoOpenBrace() {
        assertThatThrownBy(() -> ImapLiteral.parseLiteralHeader("123}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid literal header");
    }

    @Test void testParseLiteralHeaderInvalidNoCloseBrace() {
        assertThatThrownBy(() -> ImapLiteral.parseLiteralHeader("{123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid literal header");
    }

    @Test void testParseLiteralHeaderEmptyString() {
        assertThatThrownBy(() -> ImapLiteral.parseLiteralHeader(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void testToString() {
        var lit = new ImapLiteral(new byte[10], false);
        assertThat(lit.toString()).isEqualTo("{10} (10 bytes)");
    }

    @Test void testToStringNonSync() {
        var lit = new ImapLiteral(new byte[7], true);
        assertThat(lit.toString()).isEqualTo("{7+} (7 bytes)");
    }

    @Test void testDataIsClonedOnConstruction() {
        byte[] original = new byte[]{1, 2, 3};
        var lit = new ImapLiteral(original, false);
        original[0] = 99; // mutate after construction
        assertThat(lit.data()[0]).isNotEqualTo((byte)99); // should still be original value
    }

    @Test void testUnicodeData() {
        String unicode = "\u65E5\u672C\u8A9E"; // 日本語
        var lit = ImapLiteral.of(unicode);
        assertThat(lit.asString()).isEqualTo(unicode);
        assertThat(lit.size()).isEqualTo(unicode.getBytes(StandardCharsets.UTF_8).length);
    }

    @Test void testLargeLiteral() {
        byte[] large = new byte[1024];
        for (int i = 0; i < 1024; i++) large[i] = (byte)i;
        var lit = new ImapLiteral(large, false);
        assertThat(lit.size()).isEqualTo(1024);
        assertThat(lit.header()).isEqualTo("{1024}");
    }

    @Test void testParseLargeSize() {
        long[] result = ImapLiteral.parseLiteralHeader("{999999999}");
        assertThat(result).containsExactly(999999999L, 0L);
    }
}
