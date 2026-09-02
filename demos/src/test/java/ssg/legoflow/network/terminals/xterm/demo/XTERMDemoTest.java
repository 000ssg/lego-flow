package ssg.legoflow.network.terminals.xterm.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.xterm.XTERMTerminal;
import ssg.legoflow.network.terminals.xterm.XTERMTerminal.MouseMode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class XTERMDemoTest {

    @Test
    void testMouseTracking() {
        XTERMTerminal terminal = (XTERMTerminal) XTERMTerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[?1000h");
        assertThat(terminal.mouseMode()).isEqualTo(MouseMode.NORMAL);

        terminal.feed("\033[?1000l");
        assertThat(terminal.mouseMode()).isEqualTo(MouseMode.OFF);
    }

    @Test
    void testBracketedPaste() {
        XTERMTerminal terminal = (XTERMTerminal) XTERMTerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[?2004h");
        assertThat(terminal.isBracketedPaste()).isTrue();

        terminal.feed("\033[?2004l");
        assertThat(terminal.isBracketedPaste()).isFalse();
    }

    @Test
    void testType() {
        Terminal terminal = XTERMTerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.type()).isEqualTo("xterm");
        assertThat(terminal.supportsColor()).isTrue();
    }
}
