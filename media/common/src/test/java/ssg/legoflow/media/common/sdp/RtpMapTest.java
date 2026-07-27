package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class RtpMapTest {

    @Test
    void testParseStaticType() {
        RtpMap rm = RtpMap.parse("0 PCMU/8000");

        assertThat(rm.payloadType()).isZero();
        assertThat(rm.codec()).isEqualTo("PCMU");
        assertThat(rm.clockRate()).isEqualTo(8000);
        assertThat(rm.channels()).isEmpty();
    }

    @Test
    void testParseDynamicWithChannels() {
        RtpMap rm = RtpMap.parse("96 opus/48000/2");

        assertThat(rm.payloadType()).isEqualTo(96);
        assertThat(rm.codec()).isEqualTo("opus");
        assertThat(rm.clockRate()).isEqualTo(48000);
        assertThat(rm.channels()).hasValue(2);
    }

    @Test
    void testParseH264() {
        RtpMap rm = RtpMap.parse("97 H264/90000");

        assertThat(rm.payloadType()).isEqualTo(97);
        assertThat(rm.codec()).isEqualTo("H264");
        assertThat(rm.clockRate()).isEqualTo(90000);
    }

    @Test
    void testFormat() {
        RtpMap rm = RtpMap.of(96, "opus", 48000, 2);

        assertThat(rm.format()).isEqualTo("96 opus/48000/2");
    }

    @Test
    void testFormatNoChannels() {
        RtpMap rm = RtpMap.of(97, "H264", 90000);

        assertThat(rm.format()).isEqualTo("97 H264/90000");
    }

    @Test
    void testRoundTrip() {
        String value = "96 opus/48000/2";
        RtpMap rm = RtpMap.parse(value);

        assertThat(rm.format()).isEqualTo(value);
    }

    @Test
    void testParseInvalidNoSpace() {
        assertThatThrownBy(() -> RtpMap.parse("96PCMU/8000"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testParseInvalidNoSlash() {
        assertThatThrownBy(() -> RtpMap.parse("96 PCMU"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidPayloadType() {
        assertThatThrownBy(() -> RtpMap.of(128, "X", 8000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testInvalidClockRate() {
        assertThatThrownBy(() -> RtpMap.of(96, "X", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        RtpMap rm = RtpMap.of(0, "PCMU", 8000);

        assertThat(rm.toString()).isEqualTo("a=rtpmap:0 PCMU/8000");
    }
}
