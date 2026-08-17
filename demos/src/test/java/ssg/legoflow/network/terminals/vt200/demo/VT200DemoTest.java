package ssg.legoflow.network.terminals.vt200.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt200.VT200Terminal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VT200DemoTest {

    @Test
    void testVideoReverseOn() {
        VT200Terminal terminal = (VT200Terminal) VT200Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
    }

    @Test
    void testVideoReverseOff() {
        VT200Terminal terminal = (VT200Terminal) VT200Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[52m");
        terminal.feed("\033[55m");
        assertThat(terminal.isVideoReverse()).isFalse();
    }

    @Test
    void testInheritsVT100() {
        Terminal terminal = VT200Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        assertThat(terminal.type()).isEqualTo("vt200");
        assertThat(terminal.supportsColor()).isTrue();
    }
}
