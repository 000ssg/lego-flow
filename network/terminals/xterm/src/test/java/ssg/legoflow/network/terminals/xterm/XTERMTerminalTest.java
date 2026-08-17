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
    void testType() {
        assertThat(terminal.type()).isEqualTo("xterm");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void testFeedText() {
        terminal.feed("Hello XTERM");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello XTERM");
    }

    @Test
    void testCursorMotion() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testSgrBasicColors() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
        terminal.feed("\u001B[44m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.BLUE);
    }

    @Test
    void testSgr256Foreground() {
        terminal.feed("\u001B[38;5;196m");  // Red in 256 palette
        var attr = terminal.currentAttr();
        assertThat(attr.fgMode()).isEqualTo(1);       // 256-color mode
        assertThat(attr.fgColor()).isEqualTo(196);    // color index
    }

    @Test
    void testSgr256Background() {
        terminal.feed("\u001B[48;5;21m");   // Blue in 256 palette
        var attr = terminal.currentAttr();
        assertThat(attr.bgMode()).isEqualTo(1);        // 256-color mode
        assertThat(attr.bgColor()).isEqualTo(21);     // color index
    }

    @Test
    void testSgrTrueColorForeground() {
        terminal.feed("\u001B[38;2;255;128;0m");  // Orange RGB
        var attr = terminal.currentAttr();
        assertThat(attr.fgMode()).isEqualTo(2);       // RGB mode
        assertThat(attr.fgColor()).isEqualTo(0xFF8000);
    }

    @Test
    void testSgrTrueColorBackground() {
        terminal.feed("\u001B[48;2;0;255;128m");  // Spring green RGB
        var attr = terminal.currentAttr();
        assertThat(attr.bgMode()).isEqualTo(2);        // RGB mode
        assertThat(attr.bgColor()).isEqualTo(0x00FF80);
    }

    @Test
    void testSgrUnderlineStyles() {
        terminal.feed("\u001B[4:3m");   // Curly underline
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_CURLY);
        terminal.feed("\u001B[4:0m");   // No underline
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_NONE);
    }

    @Test
    void testSgrReset() {
        terminal.feed("\u001B[38;5;100m\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
        terminal.feed("\u001B[0m");
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void testMouseTrackingNormal() {
        terminal.feed("\u001B[?1000h");  // DECSET 1000 — normal mouse
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.NORMAL);
    }

    @Test
    void testMouseTrackingHighlight() {
        terminal.feed("\u001B[?1002h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.HIGHLIGHT);
    }

    @Test
    void testMouseTrackingCellMotion() {
        terminal.feed("\u001B[?1003h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.CELL_MOTION);
    }

    @Test
    void testMouseTrackingOff() {
        terminal.feed("\u001B[?1000h");
        terminal.feed("\u001B[?1000l");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.OFF);
    }

    @Test
    void testSgrMouseMode() {
        terminal.feed("\u001B[?1006h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isSgrMouse()).isTrue();
        terminal.feed("\u001B[?1006l");
        assertThat(xterm.isSgrMouse()).isFalse();
    }

    @Test
    void testBracketedPaste() {
        terminal.feed("\u001B[?2004h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isBracketedPaste()).isTrue();
        terminal.feed("\u001B[?2004l");
        assertThat(xterm.isBracketedPaste()).isFalse();
    }

    @Test
    void testSyncMode() {
        terminal.feed("\u001B[?2026h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isSyncMode()).isTrue();
        terminal.feed("\u001B[?2026l");
        assertThat(xterm.isSyncMode()).isFalse();
    }

    @Test
    void testFocusTracking() {
        terminal.feed("\u001B[?1004h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isFocusTracking()).isTrue();
        terminal.feed("\u001B[?1004l");
        assertThat(xterm.isFocusTracking()).isFalse();
    }

    @Test
    void testOriginMode() {
        terminal.feed("\u001B[?6h");
        assertThat(terminal.displayModel().originMode()).isTrue();
        terminal.feed("\u001B[?6l");
        assertThat(terminal.displayModel().originMode()).isFalse();
    }

    @Test
    void testOscTitle() {
        terminal.feed("\u001B]0;XTERM Title\u0007");
        assertThat(terminal.title()).isEqualTo("XTERM Title");
    }

    @Test
    void testUrxvtMouseMode() {
        terminal.feed("\u001B[?1015h");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isUrxvtMouse()).isTrue();
        terminal.feed("\u001B[?1015l");
        assertThat(xterm.isUrxvtMouse()).isFalse();
    }

    @Test
    void testOverline() {
        terminal.feed("\u001B[53m");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.isOverline()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(xterm.isOverline()).isFalse();
    }

    @Test
    void testReset() {
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
    void testInheritsAnsiBehavior() {
        // XTERM should behave like ANSI for standard sequences
        terminal.feed("Hello\u001B[1;1H");  // ANSI cursor positioning
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    // --- Cursor Shape (DECSCUSR) ---
    @Test
    void testCursorShapeBlinkBlock() {
        terminal.feed("\u001B[1 q"); // DECSCUSR — blink block
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.BLINK_BLOCK);
    }

    @Test
    void testCursorShapeSteadyBlock() {
        terminal.feed("\u001B[2 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.STEADY_BLOCK);
    }

    @Test
    void testCursorShapeBlinkUnderline() {
        terminal.feed("\u001B[3 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.BLINK_UNDERLINE);
    }

    @Test
    void testCursorShapeSteadyUnderline() {
        terminal.feed("\u001B[4 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.STEADY_UNDERLINE);
    }

    @Test
    void testCursorShapeBlinkBar() {
        terminal.feed("\u001B[5 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.BLINK_BAR);
    }

    @Test
    void testCursorShapeSteadyBar() {
        terminal.feed("\u001B[6 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.STEADY_BAR);
    }

    @Test
    void testCursorShapeDefault() {
        terminal.feed("\u001B[0 q");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.DEFAULT);
    }

    @Test
    void testCursorShapeUnknown() {
        terminal.feed("\u001B[99 q");
        var xterm = (XTERMTerminal) terminal;
        // Unknown — should ignore, keep current
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.DEFAULT);
    }

    // --- Clipboard OSC 52 ---
    @Test
    void testClipboardWrite() {
        terminal.feed("\u001B]52;CLIPBOARD;" + java.util.Base64.getEncoder().encodeToString("Hello".getBytes()) + "\u0007");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.clipboardData()).isEqualTo("Hello");
    }

    @Test
    void testClipboardQuery() {
        terminal.feed("\u001B]52;?;" + java.util.Base64.getEncoder().encodeToString("Data".getBytes()) + "\u0007");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.clipboardData()).isEqualTo("Data");
    }

    @Test
    void testPrimarySelection() {
        terminal.feed("\u001B]52;PRIMARY;" + java.util.Base64.getEncoder().encodeToString("Primary".getBytes()) + "\u0007");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.primarySelection()).isEqualTo("Primary");
    }

    @Test
    void testPrimarySelectionShortForm() {
        terminal.feed("\u001B]52;p;" + java.util.Base64.getEncoder().encodeToString("P".getBytes()) + "\u0007");
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.primarySelection()).isEqualTo("P");
    }

    @Test
    void testSecondarySelection() {
        terminal.feed("\u001B]52;S;" + java.util.Base64.getEncoder().encodeToString("S".getBytes()) + "\u0007");
        var xterm = (XTERMTerminal) terminal;
        // Secondary treated as primary
        assertThat(xterm.primarySelection()).isEqualTo("S");
    }

    @Test
    void testClipboardInvalidBase64() {
        terminal.feed("\u001B]52;CLIPBOARD;not-valid-base64!!!\u0007");
        var xterm = (XTERMTerminal) terminal;
        // Should not crash
        assertThat(xterm.clipboardData()).isNull();
    }

    // --- DCS DECRQSS ---
    @Test
    void testDcsDecrqss() {
        terminal.feed("\u001BPq\u001B\\"); // DCS q ST
        var xterm = (XTERMTerminal) terminal;
        // Should not throw, DECRQSS recognized
        assertThat(xterm.cursorStyle()).isEqualTo(XTERMTerminal.CursorStyle.DEFAULT);
    }

    // --- OSC color theme queries ---
    @Test
    void testOscColorTheme10() {
        terminal.feed("\u001B]10;;\u0007"); // OSC 10 — foreground color query
        // Should not throw
    }

    @Test
    void testOscColorTheme11() {
        terminal.feed("\u001B]11;;\u0007"); // OSC 11 — background color query
        // Should not throw
    }

    @Test
    void testOscColorTheme12() {
        terminal.feed("\u001B]12;;\u0007"); // OSC 12 — cursor color query
        // Should not throw
    }

    // --- OSC icon title ---
    @Test
    void testOscIconTitle() {
        terminal.feed("\u001B]1;MyIcon\u0007");
        assertThat(terminal.displayModel().iconTitle()).isEqualTo("MyIcon");
    }

    // --- OSC 7 — current working directory ---
    @Test
    void testOscWorkingDirectory() {
        terminal.feed("\u001B]7;file:///home/user\u0007");
        // Should not throw
    }

    // --- Multiple DECSET modes ---
    @Test
    void testMultipleDecsetModes() {
        terminal.feed("\u001B[?1000;1006h"); // Multiple DECSET in one sequence
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.NORMAL);
        assertThat(xterm.isSgrMouse()).isTrue();
    }

    // --- SGR double underline ---
    @Test
    void testSgrDoubleUnderline() {
        terminal.feed("\u001B[4:2m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_DOUBLE);
    }

    @Test
    void testSgrCurlyUnderline() {
        terminal.feed("\u001B[4:3m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_CURLY);
    }

    @Test
    void testSgrDottedUnderline() {
        terminal.feed("\u001B[4:4m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_DOTTED);
    }

    @Test
    void testSgrDashedUnderline() {
        terminal.feed("\u001B[4:5m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_DASHED);
    }

    // --- Mouse mode transition ---
    @Test
    void testMouseModeTransition() {
        terminal.feed("\u001B[?1000h"); // NORMAL
        terminal.feed("\u001B[?1002h"); // HIGHLIGHT
        var xterm = (XTERMTerminal) terminal;
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.HIGHLIGHT);
        terminal.feed("\u001B[?1003h"); // CELL_MOTION
        assertThat(xterm.mouseMode()).isEqualTo(XTERMTerminal.MouseMode.CELL_MOTION);
    }

    // --- DECRQM — inherited from VT100 (through ANSI) ---
    @Test
    void testDecrqmInherited() {
        // DECRQM works through XTERM -> ANSI -> VT100 inheritance
        XTERMTerminal xterm = (XTERMTerminal) terminal;
        xterm.feed("\u001B[?1$p");
        String output = xterm.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;3$y");
    }

    @Test
    void testDecrqmXtermMouseMode() {
        // Query XTERM mouse mode (1000) — returns not recognized (0) from VT100 base
        // XTERM-specific DECRQM support: left as known limitation — terminal emulators
        // typically don't support querying DECSET 1000+ via DECRQM; DECRQSS is used instead
        XTERMTerminal xterm = (XTERMTerminal) terminal;
        xterm.feed("\u001B[?1000$p");
        String output = xterm.readOutput();
        assertThat(output).isEqualTo("\u001B[?1000;0$y");
    }
}
