package ssg.legoflow.network.terminals.vt200;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT200TerminalTest {

    private VT200Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT200Terminal) VT200Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("vt200");
    }

    @Test
    void inheritsVt100Features() {
        terminal.feed("Hello");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
    }

    @Test
    void videoReverse() {
        assertThat(terminal.isVideoReverse()).isFalse();
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(terminal.isVideoReverse()).isFalse();
    }

    @Test
    void cursorPosition() {
        terminal.feed("\u001B[5;10H");
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void sgrBold() {
        terminal.feed("\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void reset() {
        terminal.feed("\u001B[52m");
        terminal.reset();
        assertThat(terminal.isVideoReverse()).isFalse();
    }
}
