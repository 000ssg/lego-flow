package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TimingTest {

    @Test
    void testParsePermanent() {
        Timing t = Timing.parse("0 0");

        assertThat(t.startTime()).isZero();
        assertThat(t.stopTime()).isZero();
        assertThat(t).isEqualTo(Timing.PERMANENT);
    }

    @Test
    void testParseWithTimestamps() {
        Timing t = Timing.parse("3034423619 3042462419");

        assertThat(t.startTime()).isEqualTo(3034423619L);
        assertThat(t.stopTime()).isEqualTo(3042462419L);
    }

    @Test
    void testFormat() {
        Timing t = new Timing(3034423619L, 3042462419L);

        assertThat(t.format()).isEqualTo("3034423619 3042462419");
    }

    @Test
    void testRoundTrip() {
        String line = "3034423619 3042462419";
        Timing t = Timing.parse(line);

        assertThat(t.format()).isEqualTo(line);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> Timing.parse("0"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        assertThat(Timing.PERMANENT.toString()).isEqualTo("t=0 0");
    }
}
