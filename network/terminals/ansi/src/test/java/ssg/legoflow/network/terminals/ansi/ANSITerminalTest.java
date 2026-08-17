package ssg.legoflow.network.terminals.ansi;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class ANSITerminalTest {

    private ANSITerminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (ANSITerminal) ANSITerminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("ansi");
    }

    @Test
    void colorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void ignoresDecPrivateModes() {
        terminal.feed("\u001B[?6h"); // DECSET origin mode — ignored
        terminal.feed("\u001B[5;5H");
        // Without origin mode, position is absolute
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void ansiSequencesWork() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void sgrWorks() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.RED);
    }

    @Test
    void feedText() {
        terminal.feed("ANSI Terminal");
        assertThat(terminal.render().get(0)).startsWith("ANSI Terminal");
    }
}
