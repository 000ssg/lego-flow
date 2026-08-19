package ssg.legoflow.email.smtp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link DotStuffing}.
 */
class DotStuffingTest {

    @Test
    void testStuffNoDots() {
        String body = "Hello World\r\nSecond line\r\nThird line";
        assertThat(DotStuffing.stuff(body)).isEqualTo(body);
    }

    @Test
    void testStuffLineStartingWithDot() {
        String body = ".leading dot\r\nnormal line";
        assertThat(DotStuffing.stuff(body)).isEqualTo("..leading dot\r\nnormal line");
    }

    @Test
    void testStuffMultipleDotLines() {
        String body = ".first\r\n.second\r\nnormal";
        assertThat(DotStuffing.stuff(body)).isEqualTo("..first\r\n..second\r\nnormal");
    }

    @Test
    void testStuffSingleDotLine() {
        String body = "before\r\n.\r\nafter";
        assertThat(DotStuffing.stuff(body)).isEqualTo("before\r\n..\r\nafter");
    }

    @Test
    void testStuffDoubleDot() {
        String body = "..already doubled";
        assertThat(DotStuffing.stuff(body)).isEqualTo("...already doubled");
    }

    @Test
    void testStuffEmpty() {
        assertThat(DotStuffing.stuff("")).isEmpty();
        assertThat(DotStuffing.stuff(null)).isEmpty();
    }

    @Test
    void testUnstuffNoDots() {
        String body = "Hello World\r\nSecond line";
        assertThat(DotStuffing.unstuff(body)).isEqualTo(body);
    }

    @Test
    void testUnstuffDoubledDots() {
        String stuffed = "..leading dot\r\nnormal line";
        assertThat(DotStuffing.unstuff(stuffed)).isEqualTo(".leading dot\r\nnormal line");
    }

    @Test
    void testUnstuffEndOfData() {
        String stuffed = "first line\r\nsecond line\r\n.";
        assertThat(DotStuffing.unstuff(stuffed)).isEqualTo("first line\r\nsecond line");
    }

    @Test
    void testUnstuffStopsAtEndOfData() {
        String stuffed = "first\r\n.\r\nthis should not appear";
        assertThat(DotStuffing.unstuff(stuffed)).isEqualTo("first");
    }

    @Test
    void testUnstuffEmpty() {
        assertThat(DotStuffing.unstuff("")).isEmpty();
        assertThat(DotStuffing.unstuff(null)).isEmpty();
    }

    @Test
    void testRoundTrip() {
        String original = "Hello\r\n.dot line\r\n..double dot\r\nnormal\r\n.end";
        String stuffed = DotStuffing.stuff(original);
        String unstuffed = DotStuffing.unstuff(stuffed);
        assertThat(unstuffed).isEqualTo(original);
    }

    @Test
    void testRoundTripWithCRLF() {
        String original = "Line 1\r\nLine 2\r\n.Line 3\r\nLine 4";
        assertThat(DotStuffing.unstuff(DotStuffing.stuff(original))).isEqualTo(original);
    }

    @Test
    void testStuffLine() {
        assertThat(DotStuffing.stuffLine(".hello")).isEqualTo("..hello");
        assertThat(DotStuffing.stuffLine("hello")).isEqualTo("hello");
        assertThat(DotStuffing.stuffLine(".")).isEqualTo("..");
        assertThat(DotStuffing.stuffLine(null)).isNull();
    }

    @Test
    void testUnstuffLine() {
        assertThat(DotStuffing.unstuffLine("..hello")).isEqualTo(".hello");
        assertThat(DotStuffing.unstuffLine("hello")).isEqualTo("hello");
        assertThat(DotStuffing.unstuffLine(".")).isNull(); // end-of-data
        assertThat(DotStuffing.unstuffLine(null)).isNull();
    }

    @Test
    void testIsEndOfData() {
        assertThat(DotStuffing.isEndOfData(".")).isTrue();
        assertThat(DotStuffing.isEndOfData("..")).isFalse();
        assertThat(DotStuffing.isEndOfData("")).isFalse();
        assertThat(DotStuffing.isEndOfData(null)).isFalse();
        assertThat(DotStuffing.isEndOfData("hello")).isFalse();
    }

    @Test
    void testStuffLineWithUnixLineEndings() {
        String body = ".first\nsecond\n.third";
        String stuffed = DotStuffing.stuff(body);
        assertThat(stuffed).startsWith("..first");
    }

    @Test
    void testEndOfDataConstant() {
        assertThat(DotStuffing.END_OF_DATA).isEqualTo(".");
    }
}
