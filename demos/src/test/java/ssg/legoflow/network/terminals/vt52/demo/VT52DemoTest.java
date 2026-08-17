package ssg.legoflow.network.terminals.vt52.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt52.VT52Terminal;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VT52DemoTest {

    @Test
    void testVT52CursorAddressing() {
        Terminal terminal = VT52Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        // VT52 cursor addressing: ESC Y row col, where row/col are value + 32
        // row=5 -> 37='%'; col=10 -> 42='*'
        terminal.feed("\033Y%*");
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void testVT52TextDisplay() {
        Terminal terminal = VT52Terminal.create(TerminalConfig.builder()
                .rows(10).cols(40).build());
        terminal.feed("Hello VT52");

        List<String> lines = terminal.render();
        assertThat(lines).hasSize(10);
        assertThat(lines.get(0)).contains("Hello VT52");
    }

    @Test
    void testNoColor() {
        Terminal terminal = VT52Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        assertThat(terminal.supportsColor()).isFalse();
        assertThat(terminal.type()).isEqualTo("vt52");
    }
}
