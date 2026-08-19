package ssg.legoflow.network.terminals.vt400.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt400.VT400Terminal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VT400DemoTest {

    @Test
    void testWindowSelection() {
        Terminal terminal = VT400Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[1t");
        // VT400Terminal.activeWindow() requires cast
        assertThat(((VT400Terminal) terminal).activeWindow()).isEqualTo(1);

        terminal.feed("\033[2t");
        assertThat(((VT400Terminal) terminal).activeWindow()).isEqualTo(2);
    }

    @Test
    void testType() {
        Terminal terminal = VT400Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.type()).isEqualTo("vt400");
    }
}
