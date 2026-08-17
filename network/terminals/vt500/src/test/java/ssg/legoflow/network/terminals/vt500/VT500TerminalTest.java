package ssg.legoflow.network.terminals.vt500;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.vt500.VT500Terminal.CharSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT500TerminalTest {

    private VT500Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT500Terminal) VT500Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt500");
    }

    @Test
    void testDefaultCharset() {
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
    }

    @Test
    void testSetG0() {
        terminal.setG0(CharSet.DEC_SPECIAL);
        terminal.selectG0();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testSetG1() {
        terminal.setG1(CharSet.GERMAN);
        terminal.selectG1();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.GERMAN);
    }

    @Test
    void testInheritsVt400Features() {
        terminal.feed("Hello");
        assertThat(terminal.render().get(0)).startsWith("Hello");
    }

    @Test
    void testResetRestoresCharsets() {
        terminal.setG0(CharSet.DEC_SPECIAL);
        terminal.selectG0();
        terminal.reset();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
    }
}
