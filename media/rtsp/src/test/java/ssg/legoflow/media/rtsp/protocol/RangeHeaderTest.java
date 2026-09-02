package ssg.legoflow.media.rtsp.protocol;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
/**
 * Tests for {@link RangeHeader}.
 */
class RangeHeaderTest {

    @Test
    void testParseNptOpen() {
        var range = RangeHeader.parse("npt=0-");
        assertThat(range.type()).isEqualTo(RangeHeader.Type.NPT);
        assertThat(range.startTime()).isEqualTo("0");
        assertThat(range.endTime()).isEmpty();
    }

    @Test
    void testParseNptRange() {
        var range = RangeHeader.parse("npt=10.5-30.0");
        assertThat(range.type()).isEqualTo(RangeHeader.Type.NPT);
        assertThat(range.startTime()).isEqualTo("10.5");
        assertThat(range.endTime()).hasValue("30.0");
    }

    @Test
    void testParseNptSeconds() {
        var range = RangeHeader.parse("npt=25.5-");
        assertThat(range.startAsSeconds()).hasValue(25.5);
    }

    @Test
    void testParseNptHhMmSs() {
        var range = RangeHeader.parse("npt=1:30:00.0-");
        assertThat(range.startAsSeconds()).hasValue(5400.0);
    }

    @Test
    void testParseClock() {
        var range = RangeHeader.parse("clock=20260606T120000Z-");
        assertThat(range.type()).isEqualTo(RangeHeader.Type.CLOCK);
        assertThat(range.startTime()).isEqualTo("20260606T120000Z");
    }

    @Test
    void testParseSmpte() {
        var range = RangeHeader.parse("smpte=00:10:20:00-");
        assertThat(range.type()).isEqualTo(RangeHeader.Type.SMPTE);
        assertThat(range.startTime()).isEqualTo("00:10:20:00");
    }

    @Test
    void testParseInvalidThrows() {
        assertThatThrownBy(() -> RangeHeader.parse("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseUnknownTypeThrows() {
        assertThatThrownBy(() -> RangeHeader.parse("unknown=0-"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNptFrom() {
        var range = RangeHeader.nptFrom(10.0);
        assertThat(range.type()).isEqualTo(RangeHeader.Type.NPT);
        assertThat(range.startTime()).isEqualTo("10");
        assertThat(range.endTime()).isEmpty();
    }

    @Test
    void testNptRange() {
        var range = RangeHeader.nptRange(5.0, 20.0);
        assertThat(range.startTime()).isEqualTo("5");
        assertThat(range.endTime()).hasValue("20");
    }

    @Test
    void testNptBeginning() {
        var range = RangeHeader.nptBeginning();
        assertThat(range.type()).isEqualTo(RangeHeader.Type.NPT);
        assertThat(range.startTime()).isEqualTo("0");
    }

    @Test
    void testFormat() {
        var range = RangeHeader.parse("npt=10-20");
        assertThat(range.format()).isEqualTo("npt=10-20");
    }

    @Test
    void testFormatOpenEnd() {
        var range = RangeHeader.parse("npt=0-");
        assertThat(range.format()).isEqualTo("npt=0-");
    }

    @Test
    void testFormatClock() {
        var range = RangeHeader.parse("clock=20260101T000000Z-20260101T010000Z");
        assertThat(range.format()).startsWith("clock=");
    }

    @Test
    void testRoundTrip() {
        String original = "npt=5.5-120.75";
        var range = RangeHeader.parse(original);
        assertThat(range.format()).isEqualTo(original);
    }

    @Test
    void testStartAsSecondsNonNpt() {
        var range = RangeHeader.parse("clock=20260101T000000Z-");
        assertThat(range.startAsSeconds()).isEmpty();
    }

    @Test
    void testToString() {
        var range = RangeHeader.parse("npt=0-");
        assertThat(range.toString()).isEqualTo("Range: npt=0-");
    }

    @Test
    void testNptFractionalSeconds() {
        var range = RangeHeader.nptFrom(10.5);
        assertThat(range.startTime()).isEqualTo("10.5");
        assertThat(range.startAsSeconds()).hasValue(10.5);
    }
}
