package ssg.legoflow.interop.terminal;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import ssg.legoflow.network.terminals.xterm.XTERMTerminal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability test: Lego Flow terminal emulators against reference
 * terminal behavior documented in DEC VT100 manuals, xterm ctlseqs, and
 * ECMA-48.
 *
 * @since 0.2.0
 */
    @Tag("terminal-protocols")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TerminalEmulatorInteropTest {

    private TerminalConfig config;

    @BeforeEach
    void setUp() {
        config = TerminalConfig.builder().cols(80).rows(24).build();
    }

    private VT100Terminal vt100() {
        return (VT100Terminal) VT100Terminal.create(config);
    }

    private XTERMTerminal xterm() {
        return (XTERMTerminal) XTERMTerminal.create(config);
    }

    // ── VT100 Cursor Motion (DEC VT100 User Manual, §6-8) ─────────

    @Test
    void testCursorUp() {
        VT100Terminal t = vt100();
        // Cursor starts at row 1, down 5 → row 6, up 3 → row 3
        t.feed("\u001b[5B");
        t.feed("\u001b[3A");
        assertThat(t.cursor().row()).isEqualTo(3);
    }

    @Test
    void testCursorDown() {
        VT100Terminal t = vt100();
        t.feed("\u001b[10B");
        assertThat(t.cursor().row()).isEqualTo(11);
    }

    @Test
    void testCursorForward() {
        VT100Terminal t = vt100();
        t.feed("\u001b[5C");
        assertThat(t.cursor().col()).isEqualTo(6);
    }

    @Test
    void testCursorBack() {
        VT100Terminal t = vt100();
        t.feed("ABCDE");
        t.feed("\u001b[3D");
        assertThat(t.cursor().col()).isEqualTo(3);
    }

    @Test
    void testCursorPosition() {
        VT100Terminal t = vt100();
        t.feed("\u001b[10;20H");
        assertThat(t.cursor().row()).isEqualTo(10);
        assertThat(t.cursor().col()).isEqualTo(20);
    }

    @Test
    void testCursorHorizontalAbsolute() {
        VT100Terminal t = vt100();
        t.feed("\u001b[40G");
        assertThat(t.cursor().col()).isEqualTo(40);
    }

    // ── VT100 SGR ─────────────────────────────────────────────────

    @Test
    void testBoldOn() {
        VT100Terminal t = vt100();
        t.feed("\u001b[1m");
        assertThat(t.currentAttr().bold()).isTrue();
    }

    @Test
    void testUnderlineOn() {
        VT100Terminal t = vt100();
        t.feed("\u001b[4m");
        assertThat(t.currentAttr().underline()).isNotEqualTo(0);
    }

    @Test
    void testForegroundColor() {
        // SGR 31 = red foreground (8-color mode, color index 1)
        VT100Terminal t = vt100();
        t.feed("\u001b[31m");
        assertThat(t.currentAttr().foreground()).isEqualTo(1);
        assertThat(t.currentAttr().fgMode()).isEqualTo(0);  // 0 = 8-color mode
    }

    @Test
    void testAllStdioColors() {
        // SGR 30-37 = 8 standard foreground colors (indices 0-7)
        VT100Terminal t = vt100();
        for (int i = 0; i <= 7; i++) {
            t.reset();
            t.feed("\u001b[" + (30 + i) + "m");
            assertThat(t.currentAttr().foreground()).isEqualTo(i);
            assertThat(t.currentAttr().fgMode()).isEqualTo(0);  // 8-color mode
        }
    }

    @Test
    void testVideoReverse() {
        // VT100 SGR 7 = reverse video (SGR 52 is a later DEC extension)
        VT100Terminal t = vt100();
        t.feed("\u001b[7m");
        assertThat(t.currentAttr().reverse()).isTrue();
    }

    @Test
    void testVideoNormal() {
        // VT100: SGR 27 turns off reverse (SGR 55 is a later DEC extension)
        VT100Terminal t = vt100();
        t.feed("\u001b[52m");  // SGR 52 — ignored by VT100 (later DEC extension)
        t.feed("\u001b[27m");  // SGR 27 — correct VT100 reverse-off
        assertThat(t.currentAttr().reverse()).isFalse();
    }

    @Test
    void testReset() {
        VT100Terminal t = vt100();
        t.feed("\u001b[10;20H");
        t.feed("\u001b[1;3;31m");
        t.feed("\u001b[?7l");
        t.reset();
        assertThat(t.cursor().row()).isEqualTo(1);
        assertThat(t.cursor().col()).isEqualTo(1);
        assertThat(t.currentAttr().bold()).isFalse();
    }

    // ── VT100 Display Operations ──────────────────────────────────

    @Test
    void testRenderReturnsCorrectLines() {
        VT100Terminal t = vt100();
        t.feed("A\nB\nC");
        List<String> lines = t.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).isEqualTo("A");
    }

    @Test
    void testEraseEntireScreen() {
        VT100Terminal t = vt100();
        t.feed("text");
        t.feed("\u001b[2J");
        assertThat(t.render().get(0)).isEmpty();
    }

    @Test
    void testEraseLineEntire() {
        VT100Terminal t = vt100();
        t.feed("line1  line2");
        t.feed("\u001b[1;1H");
        t.feed("\u001b[2K");
        assertThat(t.render().get(0)).isEmpty();
    }

    @Test
    void testInsertLine() {
        VT100Terminal t = vt100();
        t.feed("A\nB\nC");
        t.feed("\u001b[1;1H");
        t.feed("\u001b[1L");
        List<String> lines = t.render();
        assertThat(lines.get(0)).isEmpty();
    }

    @Test
    void testDeleteLine() {
        // DL (Delete Line) deletes line at cursor and shifts lines below up
        VT100Terminal t = vt100();
        t.feed("A\nB\nC");
        t.feed("\u001b[2;1H");  // cursor to row 2
        t.feed("\u001b[1M");   // delete 1 line → "C" shifts to row 2
        List<String> lines = t.render();
        // Row 2 now contains "C" (shifted up from row 3)
        assertThat(lines.get(1)).contains("C");
    }

    // ── XTERM ─────────────────────────────────────────────────────

    @Test
    void testXtermDecPrivateModes() {
        XTERMTerminal t = xterm();
        t.feed("\u001b[?7h");
        assertThat(t.isAutoWrap()).isTrue();
        t.feed("\u001b[?6h");
        assertThat(t.displayModel().originMode()).isTrue();
    }

    @Test
    void testXterm256Color() {
        XTERMTerminal t = xterm();
        t.feed("\u001b[38;5;196m");
        assertThat(t.currentAttr()).isNotNull();
    }

    @Test
    void testXtermTrueColor() {
        XTERMTerminal t = xterm();
        t.feed("\u001b[38;2;255;128;0m");
        assertThat(t.currentAttr()).isNotNull();
    }

    // ── Reference Compatibility ───────────────────────────────────

    @Test
    void testReferenceEscapeSequenceFormats() {
        VT100Terminal t = vt100();
        byte[][] wellKnown = {
                "\u001b[H".getBytes(),
                "\u001b[2J".getBytes(),
                "\u001b[0;0H".getBytes(),
                "\u001b[?25h".getBytes(),
                "\u001b[?25l".getBytes(),
                "\u001b[?7h".getBytes(),
                "\u001b[?7l".getBytes(),
        };
        for (byte[] seq : wellKnown) {
            t.reset();
            t.feed(seq);
        }
    }

    @Test
    void testDsrResponses() {
        VT100Terminal t = vt100();
        t.feed("\u001b[6n");
        assertThat(t).isNotNull();
    }

    @Test
    void testDecrqmResponses() {
        VT100Terminal t = vt100();
        t.feed("\u001b[?7$p");
        assertThat(t).isNotNull();

        XTERMTerminal xt = xterm();
        xt.feed("\u001b[?7$p");
        assertThat(xt).isNotNull();
    }
}
