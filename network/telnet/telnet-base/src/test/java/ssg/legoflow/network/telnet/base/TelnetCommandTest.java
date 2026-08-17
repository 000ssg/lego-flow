package ssg.legoflow.network.telnet.base;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetCommandTest {

    @Test
    void fromCodeKnownValues() {
        assertThat(TelnetCommand.fromCode(240)).isEqualTo(TelnetCommand.SE);
        assertThat(TelnetCommand.fromCode(241)).isEqualTo(TelnetCommand.NOP);
        assertThat(TelnetCommand.fromCode(242)).isEqualTo(TelnetCommand.DM);
        assertThat(TelnetCommand.fromCode(243)).isEqualTo(TelnetCommand.BRK);
        assertThat(TelnetCommand.fromCode(244)).isEqualTo(TelnetCommand.IP);
        assertThat(TelnetCommand.fromCode(245)).isEqualTo(TelnetCommand.AO);
        assertThat(TelnetCommand.fromCode(246)).isEqualTo(TelnetCommand.AYT);
        assertThat(TelnetCommand.fromCode(247)).isEqualTo(TelnetCommand.EC);
        assertThat(TelnetCommand.fromCode(248)).isEqualTo(TelnetCommand.EL);
        assertThat(TelnetCommand.fromCode(249)).isEqualTo(TelnetCommand.GA);
        assertThat(TelnetCommand.fromCode(250)).isEqualTo(TelnetCommand.SB);
        assertThat(TelnetCommand.fromCode(251)).isEqualTo(TelnetCommand.WILL);
        assertThat(TelnetCommand.fromCode(252)).isEqualTo(TelnetCommand.WONT);
        assertThat(TelnetCommand.fromCode(253)).isEqualTo(TelnetCommand.DO);
        assertThat(TelnetCommand.fromCode(254)).isEqualTo(TelnetCommand.DONT);
    }

    @Test
    void fromCodeUnknown() {
        assertThat(TelnetCommand.fromCode(0)).isNull();
        assertThat(TelnetCommand.fromCode(100)).isNull();
        assertThat(TelnetCommand.fromCode(239)).isNull();
    }

    @Test
    void hasOption() {
        assertThat(TelnetCommand.WILL.hasOption()).isTrue();
        assertThat(TelnetCommand.WONT.hasOption()).isTrue();
        assertThat(TelnetCommand.DO.hasOption()).isTrue();
        assertThat(TelnetCommand.DONT.hasOption()).isTrue();
        assertThat(TelnetCommand.NOP.hasOption()).isFalse();
        assertThat(TelnetCommand.AYT.hasOption()).isFalse();
        assertThat(TelnetCommand.SB.hasOption()).isFalse();
        assertThat(TelnetCommand.SE.hasOption()).isFalse();
    }

    @Test
    void codeValues() {
        for (TelnetCommand cmd : TelnetCommand.values()) {
            assertThat(cmd.code()).isBetween(240, 254);
        }
    }
}
