package ssg.legoflow.network.terminals.vt100.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class VT100DemoTest {

    @Test
    void testCursorPositioning() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testSGRAttributes() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[1;31mText");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void testCursorSaveRestore() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[5;10H");
        terminal.feed("\033[s");
        terminal.feed("\033[1;1H");
        terminal.feed("\033[u");

        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void testBasicFeatures() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(10).cols(40).build());

        assertThat(terminal.type()).isEqualTo("vt100");
        assertThat(terminal.supportsColor()).isTrue();
        assertThat(terminal.render()).hasSize(10);
    }
}
