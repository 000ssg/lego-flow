package ssg.legoflow.network.terminals.vt100;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT100TerminalTest {

    private Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = VT100Terminal.create(TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("vt100");
    }

    @Test
    void colorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void feedText() {
        terminal.feed("Hello VT100");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello VT100");
    }

    @Test
    void cursorUp() {
        terminal.feed("\u001B[3;10H");
        terminal.feed("\u001B[2A");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void cursorDown() {
        terminal.feed("\u001B[3;5H");  // Go to row 3
        terminal.feed("\u001B[5B");      // Down 5
        assertThat(terminal.cursor().row()).isEqualTo(8);
    }

    @Test
    void cursorForward() {
        terminal.feed("\u001B[5C");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void cursorBack() {
        terminal.feed("\u001B[10G");     // CHA — horizontal absolute, col 10
        terminal.feed("\u001B[3D");       // CUB — back 3
        assertThat(terminal.cursor().col()).isEqualTo(7);
    }

    @Test
    void cursorPosition() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void cursorPositionDefaultParams() {
        terminal.feed("\u001B[H");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void eraseDisplayMode0() {
        terminal.feed("Hello");
        terminal.feed("\u001B[10;5H");
        terminal.feed("\u001B[J");
        // From cursor position to end should be cleared
    }

    @Test
    void eraseLineMode2() {
        terminal.feed("Hello World");
        terminal.feed("\u001B[2K");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEmpty();
    }

    @Test
    void sgrBold() {
        terminal.feed("\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void sgrUnderline() {
        terminal.feed("\u001B[4m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.UNDERLINE_SINGLE);
    }

    @Test
    void sgrForegroundColor() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.RED);
    }

    @Test
    void sgrBackgroundColor() {
        terminal.feed("\u001B[44m");
        assertThat(terminal.currentAttr().background()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.BLUE);
    }

    @Test
    void sgrReset() {
        terminal.feed("\u001B[1m\u001B[31m");
        assertThat(terminal.currentAttr().bold()).isTrue();
        terminal.feed("\u001B[0m");
        assertThat(terminal.currentAttr()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.DEFAULT);
    }

    @Test
    void sgrMultipleCodes() {
        terminal.feed("\u001B[1;31;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.RED);
        assertThat(attr.background()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.GREEN);
    }

    @Test
    void originMode() {
        terminal.feed("\u001B[?6h");  // DECSET origin mode
        terminal.feed("\u001B[20;5r"); // Scroll region 20-5 (top=20, bottom=5 is wrong)
        // Actually DECSTBM is ESC [ Pt ; Pb r — top must be <= bottom
        // Let's use a valid scroll region
        terminal.feed("\u001B[1;20r"); // Scroll region 1-20
        terminal.feed("\u001B[5;10r"); // Scroll region 5-10
        terminal.feed("\u001B[1;1H");   // CUP with origin mode: row 1 + scrollTop(5) - 1 = 5
        assertThat(terminal.cursor().row()).isEqualTo(5);
        terminal.feed("\u001B[?6l");    // DECRST origin mode
        terminal.feed("\u001B[5;5H");   // Without origin mode: absolute row 5
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void originModeWithScrollRegion() {
        terminal.feed("\u001B[5;15r");  // Scroll region 5-15
        terminal.feed("\u001B[?6h");     // DECSET origin mode
        terminal.feed("\u001B[1;1H");    // CUP(1,1) → actual row 5, col 1
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(1);
        terminal.feed("\u001B[10;1H");   // CUP(10,1) → actual row 14, col 1
        assertThat(terminal.cursor().row()).isEqualTo(14);
    }

    @Test
    void decrstOriginMode() {
        terminal.feed("\u001B[?6h");
        terminal.feed("\u001B[?6l");
        terminal.feed("\u001B[5;5H");
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void cursorSaveRestore() {
        terminal.feed("Hello");
        terminal.feed("\u001B[s");  // DECSC — save cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
        terminal.feed("\u001B[1;1H");  // Move cursor
        assertThat(terminal.cursor().row()).isEqualTo(1);
        terminal.feed("\u001B[u");  // DECRC — restore cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void insertLines() {
        terminal.feed("\u001B[2;1H");
        terminal.feed("\u001B[2L");
        // 2 lines inserted at row 2
    }

    @Test
    void deleteLines() {
        terminal.feed("\u001B[2;1H");
        terminal.feed("\u001B[2M");
        // 2 lines deleted at row 2
    }

    @Test
    void reset() {
        terminal.feed("\u001B[1m");
        terminal.reset();
        assertThat(terminal.currentAttr()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.DEFAULT);
    }

    @Test
    void oscTitle() {
        terminal.feed("\u001B]0;My Title\u0007");
        assertThat(terminal.title()).isEqualTo("My Title");
    }

    @Test
    void carriageReturn() {
        terminal.feed("Hello");
        terminal.feed("\r");
        assertThat(terminal.cursor().col()).isEqualTo(1);
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void lineFeed() {
        terminal.feed("\n");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void backspace() {
        terminal.feed("Hello");
        terminal.feed("\b");
        assertThat(terminal.cursor().col()).isEqualTo(5);
    }

    @Test
    void scrollRegion() {
        terminal.feed("\u001B[5;15r");
        assertThat(terminal.displayModel().screen().scrollTop()).isEqualTo(5);
        assertThat(terminal.displayModel().screen().scrollBottom()).isEqualTo(15);
    }

    @Test
    void repeatPreceding() {
        terminal.feed("X");
        terminal.feed("\u001B[3b");  // Repeat preceding 3 times
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("XXXXX");
    }
}
