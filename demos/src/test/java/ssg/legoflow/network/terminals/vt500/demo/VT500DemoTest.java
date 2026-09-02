package ssg.legoflow.network.terminals.vt500.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal.CharSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class VT500DemoTest {

    @Test
    void testCharsetSelection() {
        VT500Terminal terminal = (VT500Terminal) VT500Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.setG0(CharSet.DEC_SPECIAL);
        terminal.selectG0();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);

        terminal.setG1(CharSet.GERMAN);
        terminal.selectG1();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.GERMAN);
    }

    @Test
    void testDefaultCharset() {
        VT500Terminal terminal = (VT500Terminal) VT500Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
    }

    @Test
    void testType() {
        Terminal terminal = VT500Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.type()).isEqualTo("vt500");
    }
}
