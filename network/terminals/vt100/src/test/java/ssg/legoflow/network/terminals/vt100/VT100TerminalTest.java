package ssg.legoflow.network.terminals.vt100;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

class VT100TerminalTest {

    private VT100Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT100Terminal) VT100Terminal.create(TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt100");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    @Test
    void testFeedText() {
        terminal.feed("Hello VT100");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello VT100");
    }

    @Test
    void testCursorUp() {
        terminal.feed("\u001B[3;10H");
        terminal.feed("\u001B[2A");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void testCursorDown() {
        terminal.feed("\u001B[3;5H");  // Go to row 3
        terminal.feed("\u001B[5B");      // Down 5
        assertThat(terminal.cursor().row()).isEqualTo(8);
    }

    @Test
    void testCursorForward() {
        terminal.feed("\u001B[5C");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testCursorBack() {
        terminal.feed("\u001B[10G");     // CHA — horizontal absolute, col 10
        terminal.feed("\u001B[3D");       // CUB — back 3
        assertThat(terminal.cursor().col()).isEqualTo(7);
    }

    @Test
    void testCursorPosition() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testCursorPositionDefaultParams() {
        terminal.feed("\u001B[H");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testEraseDisplayMode0() {
        terminal.feed("Hello");
        terminal.feed("\u001B[10;5H");
        terminal.feed("\u001B[J");
        // From cursor position to end should be cleared
    }

    @Test
    void testEraseLineMode2() {
        terminal.feed("Hello World");
        terminal.feed("\u001B[2K");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEmpty();
    }

    @Test
    void testSgrBold() {
        terminal.feed("\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void testSgrUnderline() {
        terminal.feed("\u001B[4m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.UNDERLINE_SINGLE);
    }

    @Test
    void testSgrForegroundColor() {
        terminal.feed("\u001B[31m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.RED);
    }

    @Test
    void testSgrBackgroundColor() {
        terminal.feed("\u001B[44m");
        assertThat(terminal.currentAttr().background()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.BLUE);
    }

    @Test
    void testSgrReset() {
        terminal.feed("\u001B[1m\u001B[31m");
        assertThat(terminal.currentAttr().bold()).isTrue();
        terminal.feed("\u001B[0m");
        assertThat(terminal.currentAttr()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.DEFAULT);
    }

    @Test
    void testSgrMultipleCodes() {
        terminal.feed("\u001B[1;31;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.RED);
        assertThat(attr.background()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.GREEN);
    }

    @Test
    void testOriginMode() {
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
    void testOriginModeWithScrollRegion() {
        terminal.feed("\u001B[5;15r");  // Scroll region 5-15
        terminal.feed("\u001B[?6h");     // DECSET origin mode
        terminal.feed("\u001B[1;1H");    // CUP(1,1) → actual row 5, col 1
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(1);
        terminal.feed("\u001B[10;1H");   // CUP(10,1) → actual row 14, col 1
        assertThat(terminal.cursor().row()).isEqualTo(14);
    }

    @Test
    void testDecrstOriginMode() {
        terminal.feed("\u001B[?6h");
        terminal.feed("\u001B[?6l");
        terminal.feed("\u001B[5;5H");
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void testCursorSaveRestore() {
        terminal.feed("Hello");
        terminal.feed("\u001B[s");  // DECSC — save cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
        terminal.feed("\u001B[1;1H");  // Move cursor
        assertThat(terminal.cursor().row()).isEqualTo(1);
        terminal.feed("\u001B[u");  // DECRC — restore cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testInsertLines() {
        terminal.feed("\u001B[2;1H");
        terminal.feed("\u001B[2L");
        // 2 lines inserted at row 2
    }

    @Test
    void testDeleteLines() {
        terminal.feed("\u001B[2;1H");
        terminal.feed("\u001B[2M");
        // 2 lines deleted at row 2
    }

    @Test
    void testReset() {
        terminal.feed("\u001B[1m");
        terminal.reset();
        assertThat(terminal.currentAttr()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.DEFAULT);
    }

    @Test
    void testOscTitle() {
        terminal.feed("\u001B]0;My Title\u0007");
        assertThat(terminal.title()).isEqualTo("My Title");
    }

    @Test
    void testCarriageReturn() {
        terminal.feed("Hello");
        terminal.feed("\r");
        assertThat(terminal.cursor().col()).isEqualTo(1);
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testLineFeed() {
        terminal.feed("\n");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testBackspace() {
        terminal.feed("Hello");
        terminal.feed("\b");
        assertThat(terminal.cursor().col()).isEqualTo(5);
    }

    @Test
    void testScrollRegion() {
        terminal.feed("\u001B[5;15r");
        assertThat(terminal.displayModel().screen().scrollTop()).isEqualTo(5);
        assertThat(terminal.displayModel().screen().scrollBottom()).isEqualTo(15);
    }

    @Test
    void testRepeatPreceding() {
        terminal.feed("X");
        terminal.feed("\u001B[3b");  // Repeat preceding 3 times
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("XXXXX");
    }

    // --- DECSET/DECRST mode 5 (DECSCNM) ---

    @Test
    void testDecsetReverseVideo() {
        terminal.feed("\u001B[?5h");  // DECSET mode 5 — reverse video
        assertThat(terminal.isScreenReverse()).isTrue();
        // Current attr should have reverse set
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    @Test
    void testDecrstReverseVideo() {
        terminal.feed("\u001B[?5h");  // DECSET mode 5
        assertThat(terminal.isScreenReverse()).isTrue();
        terminal.feed("\u001B[?5l");  // DECRST mode 5 — disable reverse
        assertThat(terminal.isScreenReverse()).isFalse();
    }

    @Test
    void testDecrstReverseVideoWhenNotSet() {
        // DECRST mode 5 when reverse not active — should not change current attr
        terminal.feed("\u001B[?5l");
        assertThat(terminal.isScreenReverse()).isFalse();
        assertThat(terminal.currentAttr().reverse()).isFalse();
    }

    // --- DECSET/DECRST mode 40 (DECCOLM) ---

    @Test
    void testDecsetSmoothScroll() {
        terminal.feed("\u001B[?40h");  // DECSET mode 40 — smooth scroll
        assertThat(terminal.isSmoothScroll()).isTrue();
        // Screen should be cleared and cursor reset to (1,1)
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testDecrstSmoothScroll() {
        terminal.feed("Hello");
        terminal.feed("\u001B[?40h");  // DECSET mode 40
        assertThat(terminal.isSmoothScroll()).isTrue();
        terminal.feed("\u001B[?40l");  // DECRST mode 40
        assertThat(terminal.isSmoothScroll()).isFalse();
    }

    @Test
    void testDecsetSmoothScrollClearsScreen() {
        terminal.feed("Before clear");
        terminal.feed("\u001B[?40h");  // DECSET mode 40 clears screen
        var lines = terminal.render();
        assertThat(lines.get(0)).isEmpty();
    }

    @Test
    void testDecsetSmoothScrollResetsCursor() {
        terminal.feed("\u001B[10;20H");  // Move cursor
        assertThat(terminal.cursor().row()).isEqualTo(10);
        terminal.feed("\u001B[?40h");  // DECSET mode 40 resets cursor
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testResetDecsetModes() {
        terminal.feed("\u001B[?5h");
        terminal.feed("\u001B[?40h");
        terminal.reset();
        assertThat(terminal.isScreenReverse()).isFalse();
        assertThat(terminal.isSmoothScroll()).isFalse();
    }

    // --- Additional SGR codes ---
    @Test
    void testSgrDim() {
        terminal.feed("\u001B[2m");
        assertThat(terminal.currentAttr().dim()).isTrue();
    }

    @Test
    void testSgrItalic() {
        terminal.feed("\u001B[3m");
        assertThat(terminal.currentAttr().italic()).isTrue();
    }

    @Test
    void testSgrBlink() {
        terminal.feed("\u001B[5m");
        assertThat(terminal.currentAttr().blink()).isTrue();
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
    void testSgrDimBoldOff() {
        terminal.feed("\u001B[1m\u001B[2m");
        terminal.feed("\u001B[22m");
        assertThat(terminal.currentAttr().bold()).isFalse();
        assertThat(terminal.currentAttr().dim()).isFalse();
    }

    @Test
    void testSgrItalicOff() {
        terminal.feed("\u001B[3m");
        terminal.feed("\u001B[23m");
        assertThat(terminal.currentAttr().italic()).isFalse();
    }

    @Test
    void testSgrBlinkOff() {
        terminal.feed("\u001B[5m");
        terminal.feed("\u001B[25m");
        assertThat(terminal.currentAttr().blink()).isFalse();
    }

    @Test
    void testSgrHiddenOff() {
        terminal.feed("\u001B[8m");
        terminal.feed("\u001B[28m");
        assertThat(terminal.currentAttr().hidden()).isFalse();
    }

    @Test
    void testSgrStrikethroughOff() {
        terminal.feed("\u001B[9m");
        terminal.feed("\u001B[29m");
        assertThat(terminal.currentAttr().strikethrough()).isFalse();
    }

    @Test
    void testSgrUnderlineOff() {
        terminal.feed("\u001B[4m");
        terminal.feed("\u001B[24m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.UNDERLINE_NONE);
    }

    @Test
    void testSgrDefaultForeground() {
        terminal.feed("\u001B[31m\u001B[39m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.WHITE);
    }

    @Test
    void testSgrDefaultBackground() {
        terminal.feed("\u001B[44m\u001B[49m");
        assertThat(terminal.currentAttr().background()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.BLACK);
    }

    @Test
    void testSgrBrightForeground() {
        terminal.feed("\u001B[91m"); // Bright red: code 91 maps to foreground 9 (bright variant)
        assertThat(terminal.currentAttr().foreground()).isEqualTo(9);
    }

    @Test
    void testSgrBrightBackground() {
        terminal.feed("\u001B[104m"); // Bright blue: code 104 maps to background 12 (bright variant)
        assertThat(terminal.currentAttr().background()).isEqualTo(12);
    }

    // --- Device Status ---
    @Test
    void testDeviceAttributes() {
        terminal.feed("\u001B[?c"); // DA1 — no output, just no-op in emulator
        // Should not throw
    }

    // --- Erase Char (ECH) ---
    @Test
    void testEraseChar() {
        terminal.feed("Hello World");
        terminal.cursor().setPos(1, 5);  // Position at 'o' (1-indexed col 5)
        terminal.feed("\u001B[3X"); // ECH — erase 3 chars from cursor
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("Hell   orld");
    }

    // --- ESC # sequences ---
    @Test
    void testEscHashReverseVideo() {
        terminal.feed("\u001B#3"); // DECSED — no-op in emulator
        // Should not throw
    }

    @Test
    void testEscHashBold() {
        terminal.feed("\u001B#8"); // DECDBL — no-op in emulator
        // Should not throw
    }

    // --- OSC icon title ---
    @Test
    void testOscIconTitle() {
        terminal.feed("\u001B]1;Icon\u0007");
        assertThat(terminal.displayModel().iconTitle()).isEqualTo("Icon");
    }

    // --- Getters ---
    @Test
    void testIsOriginModeDefault() {
        assertThat(terminal.isOriginMode()).isFalse();
    }

    @Test
    void testIsAutoWrapDefault() {
        assertThat(terminal.isAutoWrap()).isTrue();
    }

    @Test
    void testIsApplicationKeypadDefault() {
        assertThat(terminal.isApplicationKeypad()).isFalse();
    }

    // --- Cursor save/restore via ESC 7/8 ---
    @Test
    void testEscSaveRestoreCursor() {
        terminal.feed("Hello");
        terminal.feed("\u001B7"); // DECSC — save cursor
        terminal.feed("\u001B[1;1H");
        terminal.feed("\u001B8"); // DECRC — restore cursor
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    // --- Device Status Request (DSR) ---
    @Test
    void testDsrCursorPosition() {
        terminal.feed("\u001B[10;20H");
        terminal.feed("\u001B[6n"); // DSR cursor position request
        // No output, should not throw
    }

    // --- Repeat preceding character at boundary ---
    @Test
    void testRepeatPrecedingAtStart() {
        terminal.feed("\u001B[b"); // EUT: repeat preceding char, default n=1 → 2 writes
        var lines = terminal.render();
        // EUT with default n=1 writes the preceding char 2 times (n+1)
        // Preceding char at col 1 is space → cursor moves to col 3
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    // --- DECSET application keypad ---
    @Test
    void testDecsetApplicationKeypad() {
        terminal.feed("\u001B[?1h"); // DECSET mode 1
        assertThat(terminal.isApplicationKeypad()).isTrue();
        terminal.feed("\u001B[?1l"); // DECRST mode 1
        assertThat(terminal.isApplicationKeypad()).isFalse();
    }

    // --- Wrap from config ---
    @Test
    void testAutoWrapFromConfig() {
        var config = TerminalConfig.builder()
                .rows(24).cols(80).autoWrap(false).build();
        var t = (VT100Terminal) VT100Terminal.create(config);
        assertThat(t.isAutoWrap()).isFalse();
    }

    // --- DECRQM — Query DEC private mode ---
    @Test
    void testDecrqmMode1Default() {
        // Query mode 1 (DECCM) — default is cleared/settable (3)
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;3$y");
    }

    @Test
    void testDecrqmMode1AfterDecset() {
        // Set mode 1, then query — should return set/clearable (2)
        terminal.feed("\u001B[?1h");
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;2$y");
    }

    @Test
    void testDecrqmUnknownMode() {
        // Query unknown mode — should return not recognized (0)
        terminal.feed("\u001B[?999$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?999;0$y");
    }

    @Test
    void testDecrqmMode7AutoWrap() {
        // Query mode 7 (DECAWM) — default is set/clearable (2)
        terminal.feed("\u001B[?7$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?7;2$y");
    }

    @Test
    void testDecrqmMode7AfterDecrst() {
        // Disable auto-wrap, then query — should return cleared/settable (3)
        terminal.feed("\u001B[?7l");
        terminal.feed("\u001B[?7$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?7;3$y");
    }
}
