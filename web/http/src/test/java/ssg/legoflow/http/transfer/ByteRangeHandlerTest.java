package ssg.legoflow.http.transfer;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class ByteRangeHandlerTest {

    @Test
    void testParseRangeHeaderSingleRange() {
        // When
        var ranges = ByteRangeHandler.parseRangeHeader("bytes=0-499", 1000);

        // Then
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().start()).isEqualTo(0);
        assertThat(ranges.getFirst().end()).isEqualTo(499);
        assertThat(ranges.getFirst().length()).isEqualTo(500);
    }

    @Test
    void testParseRangeHeaderSuffixRange() {
        // When
        var ranges = ByteRangeHandler.parseRangeHeader("bytes=-100", 1000);

        // Then
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().start()).isEqualTo(900);
        assertThat(ranges.getFirst().end()).isEqualTo(999);
    }

    @Test
    void testParseRangeHeaderOpenEndedRange() {
        // When
        var ranges = ByteRangeHandler.parseRangeHeader("bytes=500-", 1000);

        // Then
        assertThat(ranges).hasSize(1);
        assertThat(ranges.getFirst().start()).isEqualTo(500);
        assertThat(ranges.getFirst().end()).isEqualTo(999);
    }

    @Test
    void testParseRangeHeaderNullReturnsEmpty() {
        assertThat(ByteRangeHandler.parseRangeHeader(null, 1000)).isEmpty();
    }

    @Test
    void testParseRangeHeaderInvalidPrefixReturnsEmpty() {
        assertThat(ByteRangeHandler.parseRangeHeader("items=0-10", 1000)).isEmpty();
    }

    @Test
    void testExtractRange() {
        // Given
        ByteBuffer content = ByteBuffer.wrap("Hello, World!".getBytes(StandardCharsets.UTF_8));
        var range = new ByteRangeHandler.ByteRange(0, 4);

        // When
        ByteBuffer extracted = ByteRangeHandler.extractRange(content, range);

        // Then
        var bytes = new byte[extracted.remaining()];
        extracted.get(bytes);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("Hello");
    }

    @Test
    void testFormatContentRange() {
        // When
        var range = new ByteRangeHandler.ByteRange(0, 499);
        String formatted = ByteRangeHandler.formatContentRange(range, 1000);

        // Then
        assertThat(formatted).isEqualTo("bytes 0-499/1000");
    }

    @Test
    void testIsRangeSatisfiable() {
        assertThat(ByteRangeHandler.isRangeSatisfiable(
                new ByteRangeHandler.ByteRange(0, 499), 1000)).isTrue();
        assertThat(ByteRangeHandler.isRangeSatisfiable(
                new ByteRangeHandler.ByteRange(0, 1000), 1000)).isFalse();
        assertThat(ByteRangeHandler.isRangeSatisfiable(
                new ByteRangeHandler.ByteRange(-1, 499), 1000)).isFalse();
    }
}
