package ssg.legoflow.network.terminals.ansi;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;
/**
 * Comprehensive tests for ANSITerminal.
 *
 * <p>ANSI X3.64 terminal filters DEC private modes (sequences with '?' intermediate).
 * All standard CSI sequences are passed through to the VT100 parent.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>DEC private modes (CSI ? n h/l) are silently ignored</li>
 *   <li>Standard CSI sequences work correctly</li>
 *   <li>SGR codes work correctly</li>
 *   <li>Cursor motion works correctly</li>
 *   <li>ESC 7/8 (save/restore cursor) work correctly</li>
 * </ul>
 */
class ANSITerminalTest {

    private ANSITerminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (ANSITerminal) ANSITerminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    // --- Basic identity tests ---

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("ansi");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    // --- DEC private mode filtering ---

    @Test
    void testDecsetOriginModeIgnored() {
        // ESC [ ? 6 h — DECSET origin mode (filtered by ANSI)
        terminal.feed("\u001B[?6h");
        // In ANSI mode, origin mode should NOT be enabled
        assertThat(terminal.displayModel().originMode()).isFalse();
    }

    @Test
    void testDecrstOriginModeIgnored() {
        terminal.feed("\u001B[?6l");
        assertThat(terminal.displayModel().originMode()).isFalse();
    }

    @Test
    void testDecsetAppCursorKeysIgnored() {
        // ESC [ ? 1 h — DECSET application cursor keys (filtered by ANSI)
        terminal.feed("\u001B[?1h");
        // In ANSI mode, application keypad should NOT be enabled
        assertThat(((ssg.legoflow.network.terminals.vt100.VT100Terminal) terminal)
                .isApplicationKeypad()).isFalse();
    }

    @Test
    void testDecsetReverseVideoIgnored() {
        // ESC [ ? 5 h — DECSET reverse video display (filtered by ANSI)
        terminal.feed("\u001B[?5h");
        // No effect in ANSI mode
    }

    @Test
    void testDecPrivateIgnoredSilently() {
        // Feed DEC private sequence, then verify standard sequences still work
        terminal.feed("\u001B[?25l");  // Hide cursor (filtered)
        terminal.feed("Hello");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
    }

    @Test
    void testDecPrivateMixedWithStandard() {
        // DEC private + standard in sequence — DEC private is filtered, standard works
        terminal.feed("\u001B[?6h");  // DECSET origin mode (filtered)
        terminal.feed("\u001B[5;10H"); // CUP (standard)
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    // --- Standard CSI sequences (should work) ---

    @Test
    void testCupCursorPosition() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testCupDefaultParams() {
        terminal.feed("\u001B[H");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testCursorUp() {
        terminal.feed("\u001B[5;1H");
        terminal.feed("\u001B[3A");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testCursorDown() {
        terminal.feed("\u001B[3;1H");
        terminal.feed("\u001B[5B");
        assertThat(terminal.cursor().row()).isEqualTo(8);
    }

    @Test
    void testCursorForward() {
        terminal.feed("\u001B[5C");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testCursorBack() {
        terminal.feed("\u001B[10G");
        terminal.feed("\u001B[3D");
        assertThat(terminal.cursor().col()).isEqualTo(7);
    }

    @Test
    void testCursorHorizontalAbsolute() {
        terminal.feed("\u001B[30G");
        assertThat(terminal.cursor().col()).isEqualTo(30);
    }

    @Test
    void testCursorVerticalAbsolute() {
        terminal.feed("\u001B[15d");
        assertThat(terminal.cursor().row()).isEqualTo(15);
    }

    @Test
    void testCursorNextLine() {
        terminal.feed("\u001B[3;10H");
        terminal.feed("\u001B[2E");
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testCursorPrevLine() {
        terminal.feed("\u001B[10;10H");
        terminal.feed("\u001B[3F");
        assertThat(terminal.cursor().row()).isEqualTo(7);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    // --- SGR codes (should work) ---

    @Test
    void testSgrReset() {
        terminal.feed("\u001B[1m");
        terminal.feed("\u001B[0m");
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void testSgrBold() {
        terminal.feed("\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void testSgrUnderline() {
        terminal.feed("\u001B[4m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_SINGLE);
    }

    @Test
    void testSgrReverse() {
        terminal.feed("\u001B[7m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    @Test
    void testSgrHidden() {
        terminal.feed("\u001B[8m");
        assertThat(terminal.currentAttr().hidden()).isTrue();
    }

    @Test
    void testSgrStrikethrough() {
        terminal.feed("\u001B[9m");
        assertThat(terminal.currentAttr().strikethrough()).isTrue();
    }

    @Test
    void testSgrForegroundColor() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testSgrBackgroundColor() {
        terminal.feed("\u001B[42m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testSgrMultipleCodes() {
        terminal.feed("\u001B[1;3;31;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.italic()).isTrue();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.GREEN);
    }

    // --- Cursor save/restore (should work) ---

    @Test
    void testEsc7SaveCursor() {
        terminal.feed("Hello");
        terminal.feed("\u001B7");  // DECSC — save cursor
        terminal.feed("\u001B[1;1H");
        terminal.feed("\u001B8");  // DECRC — restore cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testCsiSaveCursor() {
        terminal.feed("Hello");
        terminal.feed("\u001B[s");
        terminal.feed("\u001B[1;1H");
        terminal.feed("\u001B[u");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    // --- Erase operations ---

    @Test
    void testEraseDisplay() {
        terminal.feed("Hello");
        terminal.feed("\u001B[2J");  // ED — erase entire display
    }

    @Test
    void testEraseLine() {
        terminal.feed("Hello World");
        terminal.feed("\u001B[2K");  // EL — erase entire line
        var lines = terminal.render();
        assertThat(lines.get(0)).isEmpty();
    }

    // --- Scroll region ---

    @Test
    void testScrollRegion() {
        terminal.feed("\u001B[5;15r");
        assertThat(terminal.displayModel().screen().scrollTop()).isEqualTo(5);
        assertThat(terminal.displayModel().screen().scrollBottom()).isEqualTo(15);
    }

    // --- OSC title ---

    @Test
    void testOscTitle() {
        terminal.feed("\u001B]0;ANSI Title\u0007");
        assertThat(terminal.title()).isEqualTo("ANSI Title");
    }

    // --- Feed and render tests ---

    @Test
    void testFeedText() {
        terminal.feed("Hello ANSI");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello ANSI");
    }

    @Test
    void testRender() {
        terminal.feed("Line1");
        terminal.feed("\r\nLine2");
        var lines = terminal.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).startsWith("Line1");
        assertThat(lines.get(1)).startsWith("Line2");
    }

    // --- Reset tests ---

    @Test
    void testReset() {
        terminal.feed("\u001B[1;31m");
        terminal.reset();
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    // --- Display model access ---

    @Test
    void testDisplayModel() {
        assertThat(terminal.displayModel()).isNotNull();
        assertThat(terminal.displayModel().screen().rows()).isEqualTo(24);
    }

    // --- Config access ---

    @Test
    void testConfigAccess() {
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    // --- DECRQM — inherited from VT100 ---
    @Test
    void testDecrqmInherited() {
        // DECRQM works through ANSI -> VT100 inheritance
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;3$y");
    }

    @Test
    void testDecrqmMode7Inherited() {
        terminal.feed("\u001B[?7$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?7;2$y");
    }
}
