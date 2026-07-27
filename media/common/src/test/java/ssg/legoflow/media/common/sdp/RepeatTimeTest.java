package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RepeatTimeTest {

    @Test
    void testParse() {
        RepeatTime rt = RepeatTime.parse("604800 3600 0 90000");

        assertThat(rt.repeatInterval()).isEqualTo("604800");
        assertThat(rt.activeDuration()).isEqualTo("3600");
        assertThat(rt.offsets()).containsExactly("0", "90000");
    }

    @Test
    void testParseCompactNotation() {
        RepeatTime rt = RepeatTime.parse("7d 1h 0 25h");

        assertThat(rt.repeatInterval()).isEqualTo("7d");
        assertThat(rt.activeDuration()).isEqualTo("1h");
        assertThat(rt.offsets()).containsExactly("0", "25h");
    }

    @Test
    void testFormat() {
        RepeatTime rt = new RepeatTime("604800", "3600", List.of("0", "90000"));

        assertThat(rt.format()).isEqualTo("604800 3600 0 90000");
    }

    @Test
    void testRoundTrip() {
        String line = "7d 1h 0 25h";
        RepeatTime rt = RepeatTime.parse(line);

        assertThat(rt.format()).isEqualTo(line);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> RepeatTime.parse("604800 3600"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        RepeatTime rt = new RepeatTime("7d", "1h", List.of("0"));

        assertThat(rt.toString()).isEqualTo("r=7d 1h 0");
    }
}
