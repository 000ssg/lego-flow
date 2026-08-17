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
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt400");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void testWindowSelection() {
        assertThat(terminal.activeWindow()).isEqualTo(1);
        terminal.feed("\u001B[2t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    @Test
    void testWindowClamped() {
        terminal.feed("\u001B[5t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    @Test
    void testInheritsVt200Features() {
        terminal.feed("Hello");
        assertThat(terminal.render().get(0)).startsWith("Hello");
    }

    @Test
    void testResetRestoresWindow() {
        terminal.feed("\u001B[2t");
        terminal.reset();
        assertThat(terminal.activeWindow()).isEqualTo(1);
    }
}
