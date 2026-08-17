package ssg.legoflow.network.terminals.vt400;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT400TerminalTest {

    private VT400Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT400Terminal) VT400Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("vt400");
    }

    @Test
    void colorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void windowSelection() {
        assertThat(terminal.activeWindow()).isEqualTo(1);
        terminal.feed("\u001B[2t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    @Test
    void windowClamped() {
        terminal.feed("\u001B[5t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    @Test
    void inheritsVt200Features() {
        terminal.feed("Hello");
        assertThat(terminal.render().get(0)).startsWith("Hello");
    }

    @Test
    void resetRestoresWindow() {
        terminal.feed("\u001B[2t");
        terminal.reset();
        assertThat(terminal.activeWindow()).isEqualTo(1);
    }
}
