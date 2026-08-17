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
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt52");
    }

    @Test
    void testNoColorSupport() {
        assertThat(terminal.supportsColor()).isFalse();
    }

    @Test
    void testFeedText() {
        terminal.feed("Hello");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
    }

    @Test
    void testCursorRight() {
        terminal.feed("AB\u001BI"); // A, B, then cursor right
        assertThat(terminal.cursor().col()).isEqualTo(4); // A(1) B(2) I→right(3→4.. wait)
        // After "AB", cursor is at col 3. ESC I moves right to col 4.
        assertThat(terminal.cursor().col()).isEqualTo(4);
    }

    @Test
    void testCursorLeft() {
        terminal.feed("ABC\u001BF");
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    @Test
    void testCursorUp() {
        terminal.feed("\u001BS");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testCursorDown() {
        terminal.feed("\u001BR");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testCursorAddress() {
        // ESC Y row col — row 5, col 10
        // VT52 encodes as value+32: row 5 = 37='%', col 10 = 42='*'
        terminal.feed("\u001BY%*");
        assertThat(terminal.cursor().row()).isEqualTo(5);
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void testClearDisplay() {
        terminal.feed("Some text");
        terminal.feed("\u001BJ");
        assertThat(terminal.render().get(0)).isEmpty();
    }

    @Test
    void testCarriageReturn() {
        terminal.feed("Hello\rWorld");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("World");
    }

    @Test
    void testLineFeed() {
        terminal.feed("A\n"); // VT52: LF moves down
        terminal.feed("\u001BD"); // ESC D = line feed
        assertThat(terminal.cursor().row()).isEqualTo(3);
    }

    @Test
    void testClearToEndOfLine() {
        terminal.feed("Hello World");
        terminal.cursor().setPos(1, 4);
        terminal.feed("\u001BE");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("Hel");
    }

    @Test
    void testReset() {
        terminal.feed("Hello");
        terminal.reset();
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testBackspace() {
        terminal.feed("ABC\u0008");
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    @Test
    void testTab() {
        terminal.feed("\t");
        assertThat(terminal.cursor().col()).isEqualTo(9);
    }

    // --- ESC K — clear to end of screen ---
    @Test
    void testClearToEndOfScreen() {
        terminal.feed("Line one");
        terminal.feed("\n");
        terminal.feed("Line two");
        terminal.cursor().setPos(1, 3);  // Position at 'n' (1-indexed col 3)
        terminal.feed("\u001BK");        // Clear from cursor to end of screen
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("Li");  // chars at col 1-2 remain, rest cleared
        assertThat(lines.get(1)).isEmpty();        // second line cleared
    }

    // --- ESC U — reverse line feed ---
    @Test
    void testReverseLineFeed() {
        terminal.feed("\n\n\n"); // cursor at row 4
        assertThat(terminal.cursor().row()).isEqualTo(4);
        terminal.feed("\u001BU"); // reverse line feed
        assertThat(terminal.cursor().row()).isEqualTo(3);
    }

    @Test
    void testReverseLineFeedAtTopScrolls() {
        terminal.feed("\u001BU"); // at row 1 — scrolls up
        // After reverse line feed at row 1, scrollUp() is called
        // cursor stays at row 1 (can't go above)
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    // --- ESC # 3 — reverse video ---
    @Test
    void testReverseVideo() {
        terminal.feed("\u001B#3");
        assertThat(terminal.isReverseVideo()).isTrue();
        terminal.feed("X");
        var attr = terminal.currentAttr();
        assertThat(attr.reverse()).isTrue();
    }

    // --- ESC # 8 — bold ---
    @Test
    void testBold() {
        terminal.feed("\u001B#8");
        assertThat(terminal.isBold()).isTrue();
        terminal.feed("X");
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
    }

    // --- ESC # 4 — single-width (no-op) ---
    @Test
    void testSingleWidth() {
        terminal.feed("\u001B#4");
        // No-op, state should return to DATA
        terminal.feed("X");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("X");
    }

    // --- ESC # 6 — double-height (not supported) ---
    @Test
    void testDoubleHeight() {
        terminal.feed("\u001B#6");
        // Not supported, state returns to DATA
        terminal.feed("Y");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Y");
    }

    // --- feed(byte[]) ---
    @Test
    void testFeedByteArray() {
        terminal.feed("Hello".getBytes());
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
    }

    // --- Event listeners ---
    @Test
    void testAddRemoveEventListener() {
        var listener = (ssg.legoflow.network.terminals.base.event.TerminalEventListener) event -> {};
        terminal.addEventListener(listener);
        terminal.removeEventListener(listener);
    }

    // --- Simple getters ---
    @Test
    void testConfigGetter() {
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    @Test
    void testDisplayModelGetter() {
        assertThat(terminal.displayModel()).isNotNull();
        assertThat(terminal.displayModel().screen()).isNotNull();
    }

    @Test
    void testCurrentAttrDefault() {
        assertThat(terminal.currentAttr()).isEqualTo(
                ssg.legoflow.network.terminals.base.display.TermAttr.DEFAULT);
    }

    @Test
    void testTitleDefault() {
        assertThat(terminal.title()).isEmpty();
    }

    // --- Visual state: combined reverse + bold ---
    @Test
    void testReverseVideoAndBoldCombined() {
        terminal.feed("\u001B#3"); // reverse
        terminal.feed("\u001B#8"); // bold
        terminal.feed("Z");
        var attr = terminal.currentAttr();
        assertThat(attr.reverse()).isTrue();
        assertThat(attr.bold()).isTrue();
    }

    // --- Reset clears visual state ---
    @Test
    void testResetClearsVisualState() {
        terminal.feed("\u001B#3"); // reverse
        assertThat(terminal.isReverseVideo()).isTrue();
        terminal.reset();
        assertThat(terminal.isReverseVideo()).isFalse();
        assertThat(terminal.isBold()).isFalse();
    }

    // --- Keypad modes (no-op) ---
    @Test
    void testApplicationKeypadMode() {
        terminal.feed("\u001B="); // application keypad
        terminal.feed("X");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("X");
    }

    @Test
    void testNumericKeypadMode() {
        terminal.feed("\u001B>"); // numeric keypad
        terminal.feed("Y");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Y");
    }

    @Test
    void testNormalKeypadMode() {
        terminal.feed("\u001B<"); // normal keypad
        terminal.feed("Z");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Z");
    }
}
