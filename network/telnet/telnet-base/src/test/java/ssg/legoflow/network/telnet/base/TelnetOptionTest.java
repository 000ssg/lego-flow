package ssg.legoflow.network.telnet.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetOptionTest {

    @Test
    void testKnownOptions() {
        assertThat(TelnetOption.fromCode(0)).isEqualTo(TelnetOption.BINARY);
        assertThat(TelnetOption.fromCode(1)).isEqualTo(TelnetOption.ECHO);
        assertThat(TelnetOption.fromCode(3)).isEqualTo(TelnetOption.SUPPRESS_GO_AHEAD);
        assertThat(TelnetOption.fromCode(24)).isEqualTo(TelnetOption.TTYPE);
        assertThat(TelnetOption.fromCode(31)).isEqualTo(TelnetOption.NAWS);
        assertThat(TelnetOption.fromCode(34)).isEqualTo(TelnetOption.LINEMODE);
        assertThat(TelnetOption.fromCode(38)).isEqualTo(TelnetOption.ENCRYPT);
        assertThat(TelnetOption.fromCode(42)).isEqualTo(TelnetOption.TERMINAL_SPEED);
    }

    @Test
    void testUnknownOption() {
        assertThat(TelnetOption.fromCode(99)).isNull();
        assertThat(TelnetOption.fromCode(200)).isNull();
    }

    @Test
    void testCodeRoundTrip() {
        for (TelnetOption opt : TelnetOption.values()) {
            assertThat(TelnetOption.fromCode(opt.code())).isEqualTo(opt);
        }
    }
}
