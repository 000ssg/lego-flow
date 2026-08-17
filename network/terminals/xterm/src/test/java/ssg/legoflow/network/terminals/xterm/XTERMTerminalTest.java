package ssg.legoflow.network.terminals.xterm;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.io.Terminal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class XTERMTerminalTest {

    private Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = XTERMTerminal.create(TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void type() {
        assertThat(terminal.type()).isEqualTo("xterm");
    }

    @Test
    void colorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void feedText() {
        terminal.feed("Hello XTERM");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello XTERM");
    }

    @Test
    void cursorMotion() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void sgrBasicColors() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
        terminal.feed("\u001B[44m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.BLUE);
    }

    @Test
    void sgr256Foreground() {
        terminal.feed("\u001B[38;5;196m");  // Red in 256 palette
        var attr = terminal.currentAttr();
        assertThat(attr.fgMode()).isEqualTo(1);       // 256-color mode
        assertThat(attr.fgColor()).isEqualTo(196);    // color index
    }

    @Test
    void sgr256Background() {
        terminal.feed("\u001B[48;5;21m");   // Blue in 256 palette
        var attr = terminal.currentAttr();
        assertThat(attr.bgMode()).isEqualTo(1);        // 256-color mode
        assertThat(attr.bgColor()).isEqualTo(21);     // color index
    }

    @Test
    void sgrTrueColorForeground() {
        terminal.feed("\u001B[38;2;255;128;0m");  // Orange RGB
        var attr = terminal.currentAttr();
        assertThat(attr.fgMode()).isEqualTo(2);       // RGB mode
        assertThat(attr.fgColor()).isEqualTo(0xFF8000);
    }

    @Test
    void sgrTrueColorBackground() {
        terminal.feed("\u001B[48;2;0;255;128m");  // Spring green RGB
        var attr = terminal.currentAttr();
        assertThat(attr.bgMode()).isEqualTo(2);        // RGB mode
        assertThat(attr.bgColor()).isEqualTo(0x00FF80);
    }

    @Test
    void sgrUnderlineStyles() {
        terminal.feed("\u001B[4:3m");   // Curly underline
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_CURLY);
        terminal.feed("\u001B[4:0m");   // No underline
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_NONE);
    }

    @Test
    void sgrReset() {
        terminal.feed("\u001B[38;5;100m\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
        terminal.feed("\u001B[0m");
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void mouseTrackingNormal() {
        terminal.feed("\u001B[?1000h");  // DECSET 1000 — normal mouse
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.NORMAL);
    }

    @Test
    void mouseTrackingHighlight() {
        terminal.feed("\u001B[?1002h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.HIGHLIGHT);
    }

    @Test
    void mouseTrackingCellMotion() {
        terminal.feed("\u001B[?1003h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.CELL_MOTION);
    }

    @Test
    void mouseTrackingOff() {
        terminal.feed("\u001B[?1000h");
        terminal.feed("\u001B[?1000l");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.OFF);
    }

    @Test
    void sgrMouseMode() {
        terminal.feed("\u001B[?1006h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isSgrMouse()).isTrue();
        terminal.feed("\u001B[?1006l");
        assertThat(xterm.isSgrMouse()).isFalse();
    }

    @Test
    void bracketedPaste() {
        terminal.feed("\u001B[?2004h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isBracketedPaste()).isTrue();
        terminal.feed("\u001B[?2004l");
        assertThat(xterm.isBracketedPaste()).isFalse();
    }

    @Test
    void syncMode() {
        terminal.feed("\u001B[?2026h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isSyncMode()).isTrue();
        terminal.feed("\u001B[?2026l");
        assertThat(xterm.isSyncMode()).isFalse();
    }

    @Test
    void focusTracking() {
        terminal.feed("\u001B[?1004h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isFocusTracking()).isTrue();
        terminal.feed("\u001B[?1004l");
        assertThat(xterm.isFocusTracking()).isFalse();
    }

    @Test
    void originMode() {
        terminal.feed("\u001B[?6h");
        assertThat(terminal.displayModel().originMode()).isTrue();
        terminal.feed("\u001B[?6l");
        assertThat(terminal.displayModel().originMode()).isFalse();
    }

    @Test
    void oscTitle() {
        terminal.feed("\u001B]0;XTERM Title\u0007");
        assertThat(terminal.title()).isEqualTo("XTERM Title");
    }

    @Test
    void urxvtMouseMode() {
        terminal.feed("\u001B[?1015h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isUrxvtMouse()).isTrue();
        terminal.feed("\u001B[?1015l");
        assertThat(xterm.isUrxvtMouse()).isFalse();
    }

    @Test
    void overline() {
        terminal.feed("\u001B[53m");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isOverline()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(xterm.isOverline()).isFalse();
    }

    @Test
    void reset() {
        terminal.feed("\u001B[?1000h\u001B[?1006h\u001B[?2004h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.NORMAL);
        assertThat(xterm.isSgrMouse()).isTrue();
        assertThat(xterm.isBracketedPaste()).isTrue();

        terminal.reset();
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.OFF);
        assertThat(xterm.isSgrMouse()).isFalse();
        assertThat(xterm.isBracketedPaste()).isFalse();
    }

    @Test
    void inheritsAnsiBehavior() {
        // XTERM should behave like ANSI for standard sequences
        terminal.feed("Hello\u001B[1;1H");  // ANSI cursor positioning
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }
}
