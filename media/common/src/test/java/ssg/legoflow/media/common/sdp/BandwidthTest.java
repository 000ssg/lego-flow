package ssg.legoflow.media.common.sdp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class BandwidthTest {

    @Test
    void testParseCT() {
        Bandwidth bw = Bandwidth.parse("CT:128");

        assertThat(bw.modifier()).isEqualTo("CT");
        assertThat(bw.value()).isEqualTo(128);
    }

    @Test
    void testParseAS() {
        Bandwidth bw = Bandwidth.parse("AS:256");

        assertThat(bw.modifier()).isEqualTo("AS");
        assertThat(bw.value()).isEqualTo(256);
    }

    @Test
    void testFormat() {
        Bandwidth bw = new Bandwidth("CT", 128);

        assertThat(bw.format()).isEqualTo("CT:128");
    }

    @Test
    void testRoundTrip() {
        String line = "AS:512";
        Bandwidth bw = Bandwidth.parse(line);

        assertThat(bw.format()).isEqualTo(line);
    }

    @Test
    void testParseInvalid() {
        assertThatThrownBy(() -> Bandwidth.parse("CT128"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNegativeValue() {
        assertThatThrownBy(() -> new Bandwidth("CT", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testToString() {
        Bandwidth bw = new Bandwidth("AS", 64);

        assertThat(bw.toString()).isEqualTo("b=AS:64");
    }
}
