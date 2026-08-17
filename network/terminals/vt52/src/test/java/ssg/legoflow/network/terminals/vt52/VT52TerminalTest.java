package ssg.legoflow.network.terminals.vt52;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT52TerminalTest {

    private VT52Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = VT52Terminal.create(TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("vt52");
    }

    @Test
    void noColorSupport() {
        assertThat(terminal.supportsColor()).isFalse();
    }

    @Test
    void feedText() {
        terminal.feed("Hello");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
    }

    @Test
    void cursorRight() {
        terminal.feed("AB\u001BI"); // A, B, then cursor right
        assertThat(terminal.cursor().col()).isEqualTo(4); // A(1) B(2) I→right(3→4.. wait)
        // After "AB", cursor is at col 3. ESC I moves right to col 4.
        assertThat(terminal.cursor().col()).isEqualTo(4);
    }

    @Test
    void cursorLeft() {
        terminal.feed("ABC\u001BF");
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    @Test
    void cursorUp() {
        terminal.feed("\u001BS");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void cursorDown() {
        terminal.feed("\u001BR");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void cursorAddress() {
        // ESC Y row col — row 5, col 10
        // VT52 encodes as value+32: row 5 = 37='%', col 10 = 42='*'
        terminal.feed("\u001BY%*");
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void clearDisplay() {
        terminal.feed("Some text");
        terminal.feed("\u001BJ");
        assertThat(terminal.render().get(0)).isEmpty();
    }

    @Test
    void carriageReturn() {
        terminal.feed("Hello\rWorld");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("World");
    }

    @Test
    void lineFeed() {
        terminal.feed("A\n"); // VT52: LF moves down
        terminal.feed("\u001BD"); // ESC D = line feed
        assertThat(terminal.cursor().row()).isEqualTo(3);
    }

    @Test
    void clearToEndOfLine() {
        terminal.feed("Hello World");
        terminal.cursor().setPos(1, 4);
        terminal.feed("\u001BE");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("Hel");
    }

    @Test
    void reset() {
        terminal.feed("Hello");
        terminal.reset();
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void backspace() {
        terminal.feed("ABC\u0008");
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    @Test
    void tab() {
        terminal.feed("\t");
        assertThat(terminal.cursor().col()).isEqualTo(9);
    }
}
