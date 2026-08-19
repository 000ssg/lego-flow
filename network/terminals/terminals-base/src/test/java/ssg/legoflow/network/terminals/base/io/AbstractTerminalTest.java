package ssg.legoflow.network.terminals.base.io;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.event.TerminalEvent;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AbstractTerminal — the core terminal class that routes input,
 * handles control characters, and processes CSI sequences.
 */
class AbstractTerminalTest {

    private static class TestTerminal extends AbstractTerminal {
        private final java.util.List<String> csiFinalBytes = new java.util.ArrayList<>();
        private final java.util.List<String> oscData = new java.util.ArrayList<>();

        TestTerminal(TerminalConfig config) { super(config); }

        @Override public String type() { return "test"; }

        @Override
        public void handleCSI(CSIParams params) {
            csiFinalBytes.add(String.valueOf(params.finalByte()));
            super.handleCSI(params);
        }

        @Override
        public void handleOSC(String data) {
            oscData.add(data);
            super.handleOSC(data);
        }

        java.util.List<String> csiFinalBytes() { return csiFinalBytes; }
        java.util.List<String> oscData() { return oscData; }
    }

    private TestTerminal terminal;

    @BeforeEach
    void setUp() {
        terminal = new TestTerminal(TerminalConfig.builder().rows(24).cols(80).build());
    }

    // ── feed() ──

    @Test
    void testFeedString() {
        terminal.feed("Hello");
        assertThat(terminal.render().get(0)).startsWith("Hello");
    }

    @Test
    void testFeedByteArray() {
        terminal.feed("Hello".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        assertThat(terminal.render().get(0)).startsWith("Hello");
    }

    @Test
    void testFeedNullByteArray() {
        assertThatThrownBy(() -> terminal.feed((byte[]) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testFeedNullString() {
        assertThatThrownBy(() -> terminal.feed((String) null))
                .isInstanceOf(NullPointerException.class);
    }

    // ── Control characters ──

    @Test
    void testCarriageReturn() {
        terminal.feed("Hello\r");
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testBackspace() {
        terminal.feed("ABC\u0008");
        assertThat(terminal.cursor().col()).isEqualTo(3);
    }

    @Test
    void testTabForward() {
        terminal.feed("\t");
        assertThat(terminal.cursor().col()).isEqualTo(9);
    }

    @Test
    void testTabAtBoundary() {
        terminal.feed("\u001B[1;79H\t");
        assertThat(terminal.cursor().col()).isEqualTo(80);
    }

    @Test
    void testLineFeed() {
        terminal.feed("\n");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testLineFeedScrollsAtBottom() {
        terminal.feed("\u001B[24;1H\n");
        assertThat(terminal.cursor().row()).isEqualTo(24);
    }

    @Test
    void testVerticalTab() {
        terminal.feed("\u000B");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testFormFeed() {
        terminal.feed("\f");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testIndex() {
        terminal.feed("\u0084");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testIndexScrollsAtBottom() {
        terminal.feed("\u001B[24;1H\u0084");
        assertThat(terminal.cursor().row()).isEqualTo(24);
    }

    @Test
    void testReverseIndex() {
        terminal.feed("\u001B[2;1H\u0085");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testReverseIndexScrollsAtTop() {
        terminal.feed("\u0085");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testNulIgnored() {
        terminal.feed("A\u0000B");
        assertThat(terminal.render().get(0)).startsWith("A");
    }

    @Test
    void testDelIgnored() {
        terminal.feed("A\u007FB");
        assertThat(terminal.render().get(0)).startsWith("A");
    }

    // ── CSI sequences ──

    @Test
    void testCsiCursorUp() {
        terminal.feed("\u001B[2;1H\u001B[1A");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.csiFinalBytes()).contains("A");
    }

    @Test
    void testCsiCursorDown() {
        terminal.feed("\u001B[1B");
        assertThat(terminal.cursor().row()).isEqualTo(2);
    }

    @Test
    void testCsiCursorForward() {
        terminal.feed("\u001B[3C");
        assertThat(terminal.cursor().col()).isEqualTo(4);
    }

    @Test
    void testCsiCursorBack() {
        terminal.feed("\u001B[5;5H\u001B[3D");
        assertThat(terminal.cursor().col()).isEqualTo(2);
    }

    @Test
    void testCsiNextLine() {
        terminal.feed("\u001B[2;5H\u001B[1E");
        assertThat(terminal.cursor().row()).isEqualTo(3);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testCsiPrevLine() {
        terminal.feed("\u001B[5;5H\u001B[2F");
        assertThat(terminal.cursor().row()).isEqualTo(3);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testCsiHorizontalAbsolute() {
        terminal.feed("\u001B[10G");
        assertThat(terminal.cursor().col()).isEqualTo(10);
    }

    @Test
    void testCsiHorizontalAbsoluteClamped() {
        terminal.feed("\u001B[999G");
        assertThat(terminal.cursor().col()).isEqualTo(80);
    }

    @Test
    void testCsiCursorPosition() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testCsiHVP() {
        terminal.feed("\u001B[10;20f");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testCsiVerticalAbsolute() {
        terminal.feed("\u001B[15d");
        assertThat(terminal.cursor().row()).isEqualTo(15);
    }

    @Test
    void testCsiVerticalAbsoluteClamped() {
        terminal.feed("\u001B[999d");
        assertThat(terminal.cursor().row()).isEqualTo(24);
    }

    @Test
    void testCsiEraseDisplay() {
        terminal.feed("Hello\u001B[1;1H\u001B[J");
        assertThat(terminal.render().get(0)).isEmpty();
    }

    @Test
    void testCsiEraseLine() {
        terminal.feed("Hello\u001B[2K");
        assertThat(terminal.render().get(0)).isEmpty();
    }

    @Test
    void testCsiInsertLine() {
        terminal.feed("\u001B[24;1HBottom\u001B[23;1H\u001B[1L");
    }

    @Test
    void testCsiDeleteLine() {
        terminal.feed("\u001B[23;1HKeep\u001B[1;1H\u001B[1M");
    }

    @Test
    void testCsiInsertChar() {
        terminal.feed("ABCD\u001B[1;2H\u001B[1@");
        assertThat(terminal.render().get(0)).startsWith("A B");
    }

    @Test
    void testCsiDeleteChar() {
        terminal.feed("ABCD\u001B[1;2H\u001B[1P");
        assertThat(terminal.render().get(0)).startsWith("ACD");
    }

    @Test
    void testCsiEraseChar() {
        terminal.feed("ABCD\u001B[1;2H\u001B[2X");
        assertThat(terminal.render().get(0)).startsWith("A  ");
    }

    @Test
    void testUnknownCsiIgnored() {
        terminal.feed("\u001B[99z");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    // ── OSC ──

    @Test
    void testOscSetTitle() {
        terminal.feed("\u001B]0;My Terminal\u0007");
        assertThat(terminal.title()).isEqualTo("My Terminal");
        assertThat(terminal.oscData()).contains("0;My Terminal");
    }

    @Test
    void testOscSetWindowTitle() {
        terminal.feed("\u001B]2;Window Title\u0007");
        assertThat(terminal.title()).isEqualTo("Window Title");
    }

    @Test
    void testOscSetIconTitle() {
        terminal.feed("\u001B]1;Icon\u0007");
        assertThat(terminal.display().iconTitle()).isEqualTo("Icon");
    }

    // ── Event listeners ──

    @Test
    void testAddRemoveListener() {
        TerminalEventListener listener = event -> {};
        terminal.addEventListener(listener);
        terminal.removeEventListener(listener);
    }

    @Test
    void testTitleChangeEvent() {
        boolean[] titleChanged = {false};
        TerminalEventListener listener = new TerminalEventListener() {
            @Override public void onEvent(TerminalEvent event) {}
            @Override public void onTitleChange(String title) {
                if ("Test Title".equals(title)) titleChanged[0] = true;
            }
        };
        terminal.addEventListener(listener);
        terminal.feed("\u001B]0;Test Title\u0007");
        assertThat(titleChanged[0]).isTrue();
    }

    @Test
    void testIconTitleChangeEvent() {
        boolean[] iconChanged = {false};
        TerminalEventListener listener = new TerminalEventListener() {
            @Override public void onEvent(TerminalEvent event) {}
            @Override public void onIconTitleChange(String icon) {
                if ("My Icon".equals(icon)) iconChanged[0] = true;
            }
        };
        terminal.addEventListener(listener);
        terminal.feed("\u001B]1;My Icon\u0007");
        assertThat(iconChanged[0]).isTrue();
    }

    // ── reset() ──

    @Test
    void testReset() {
        terminal.feed("Hello\u001B[10;20H");
        terminal.reset();
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
        assertThat(terminal.render().get(0)).isEmpty();
    }

    // ── supportsColor() ──

    @Test
    void testSupportsColorDefault() {
        assertThat(terminal.supportsColor()).isFalse();
    }

    @Test
    void testSupportsColorWithColorDepth() {
        var t = new TestTerminal(TerminalConfig.builder().rows(24).cols(80).colorDepth(8).build());
        assertThat(t.supportsColor()).isTrue();
    }

    // ── render() ──

    @Test
    void testRenderEmpty() {
        var lines = terminal.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).isEmpty();
    }

    @Test
    void testRenderWithContent() {
        terminal.feed("Line1\nLine2");
        var lines = terminal.render();
        assertThat(lines.get(0)).contains("Line1");
        assertThat(lines.get(1)).contains("Line2");
    }

    // ── fireTitleChange() ──

    @Test
    void testFireTitleChange() {
        String[] captured = {null};
        TerminalEventListener listener = new TerminalEventListener() {
            @Override public void onEvent(TerminalEvent event) {}
            @Override public void onTitleChange(String title) { captured[0] = title; }
        };
        terminal.addEventListener(listener);
        terminal.fireTitleChange("Direct Title");
        assertThat(captured[0]).isEqualTo("Direct Title");
    }

    // ── Display access ──

    @Test
    void testDisplayAccessor() {
        assertThat(terminal.display()).isNotNull();
        assertThat(terminal.display().screen()).isNotNull();
    }

    // ── Terminal interface ──

    @Test
    void testCurrentAttr() {
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void testConfig() {
        TerminalConfig cfg = terminal.config();
        assertThat(cfg.rows()).isEqualTo(24);
        assertThat(cfg.cols()).isEqualTo(80);
    }

    @Test
    void testDisplayModel() {
        assertThat(terminal.displayModel()).isSameAs(terminal.display());
    }

    // ── Edge cases ──

    @Test
    void testFeedEmptyString() {
        terminal.feed("");
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testFeedEmptyByteArray() {
        terminal.feed(new byte[0]);
        assertThat(terminal.cursor().row()).isEqualTo(1);
    }

    @Test
    void testFeedMixedContent() {
        terminal.feed("A\u0008B\u001B[2K\rC");
        assertThat(terminal.cursor().col()).isEqualTo(2);
    }

    @Test
    void testCsiClampCursorPosition() {
        terminal.feed("\u001B[0;0H");
        assertThat(terminal.cursor().row()).isEqualTo(1);
        assertThat(terminal.cursor().col()).isEqualTo(1);
    }

    @Test
    void testCursorMoveClampedAtBoundaries() {
        terminal.feed("\u001B[1;1H\u001B[99A\u001B[99B");
        assertThat(terminal.cursor().row()).isEqualTo(24);
    }
}
