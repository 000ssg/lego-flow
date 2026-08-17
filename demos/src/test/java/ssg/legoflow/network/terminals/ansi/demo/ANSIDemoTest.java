package ssg.legoflow.network.terminals.ansi.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.ansi.ANSITerminal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ANSIDemoTest {

    @Test
    void testStandardCSISequence() {
        Terminal terminal = ANSITerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testType() {
        Terminal terminal = ANSITerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.type()).isEqualTo("ansi");
        assertThat(terminal.supportsColor()).isTrue();
    }
}
