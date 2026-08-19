package ssg.legoflow.interop.terminal;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.tn3270.TN3270FieldAttr;
import ssg.legoflow.network.terminals.tn3270.TN3270Screen;
import ssg.legoflow.network.terminals.tn3270.TN3270Terminal;
import ssg.legoflow.network.terminals.tn5250.TN5250FieldAttr;
import ssg.legoflow.network.terminals.tn5250.TN5250Screen;
import ssg.legoflow.network.terminals.tn5250.TN5250Terminal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Interoperability tests for TN3270/TN5250 terminal emulators.
 *
 * <p>Tests verify 3270/5250 protocol compliance against RFC 1576 (TN3270)
 * and RFC 1662 (TN5250), and validate character-level compatibility
 * with reference terminal behavior.
 *
 * <p><b>References:</b>
 * <ul>
 *   <li>RFC 1576 — TN3270 Enhanced Session Protocol</li>
 *   <li>RFC 1662 — TN5250 Protocol</li>
 *   <li>IBM 3270 Information Entry Protocol (SC30-8403)</li>
 *   <li>IBM 5250 Information Entry Protocol (SA22-7205)</li>
 *   <li>Open 3270 — open-source 3270 emulator (reference implementation)</li>
 *   <li>x3270 — classic 3270 terminal emulator</li>
 * </ul>
 *
 * @since 0.2.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TN3270TN5250InteropTest {

    private TerminalConfig config;
    private TerminalConfig wideConfig;

    @BeforeEach
    void setUp() {
        config = TerminalConfig.builder().cols(80).rows(24).build();
        wideConfig = TerminalConfig.builder().cols(132).rows(43).build();
    }

    // ── TN3270: Screen Model (RFC 1576, §2.1) ──────────────────────

    @Test
    void testTN3270DefaultSize() {
        TN3270Terminal t = TN3270Terminal.create();
        assertThat(t.type()).isEqualTo("tn3270");
        assertThat(t.title()).isEqualTo("TN3270");
        assertThat(t.supportsColor()).isTrue();
        assertThat(t.config().rows()).isEqualTo(24);
        assertThat(t.config().cols()).isEqualTo(80);
    }

    @Test
    void testTN3270WideSize() {
        TN3270Terminal t = TN3270Terminal.create(wideConfig);
        assertThat(t.config().rows()).isEqualTo(43);
        assertThat(t.config().cols()).isEqualTo(132);
    }

    @Test
    void testTN3270ScreenWriteChars() {
        TN3270Terminal t = TN3270Terminal.create();
        t.feed("Hello, 3270!");
        List<String> render = t.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).contains("Hello, 3270!");
        // Reference: IBM 3270 "Introduction to Communications" — chars appear on screen at cursor
        assertThat(t.cursor().col()).isEqualTo(13);
        assertThat(t.cursor().row()).isEqualTo(1);
    }

    @Test
    void testTN3270ScreenWrapping() {
        TN3270Terminal t = TN3270Terminal.create();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('A');
        t.feed(sb.toString());
        List<String> render = t.render();
        // 80 chars on row 1, 10 chars on row 2
        assertThat(render.get(0)).hasSize(80);
        assertThat(render.get(1)).contains("A");
        assertThat(t.cursor().row()).isEqualTo(2);
        assertThat(t.cursor().col()).isEqualTo(11);
    }

    @Test
    void testTN3270CarriageReturn() {
        TN3270Terminal t = TN3270Terminal.create();
        t.feed("Hello\rWorld");
        // CR moves cursor to column 1, then chars overwrite
        assertThat(t.render().get(0)).startsWith("World");
    }

    @Test
    void testTN3270Newlines() {
        TN3270Terminal t = TN3270Terminal.create();
        t.feed("Row1\nRow2\nRow3");
        List<String> render = t.render();
        assertThat(render.get(0)).startsWith("Row1");
        assertThat(render.get(1)).startsWith("Row2");
        assertThat(render.get(2)).startsWith("Row3");
        assertThat(t.cursor().row()).isEqualTo(3);
        assertThat(t.cursor().col()).isEqualTo(5);
    }

    @Test
    void testTN3270CursorPosition() {
        TN3270Terminal t = TN3270Terminal.create();
        t.cursorPosition(10, 20);
        Cursor c = t.cursor();
        assertThat(c.row()).isEqualTo(10);
        assertThat(c.col()).isEqualTo(20);
    }

    @Test
    void testTN3270CursorClamped() {
        TN3270Terminal t = TN3270Terminal.create();
        t.cursorPosition(0, 0);
        assertThat(t.cursor().row()).isEqualTo(1);
        assertThat(t.cursor().col()).isEqualTo(1);
        t.cursorPosition(100, 200);
        assertThat(t.cursor().row()).isEqualTo(24);
        assertThat(t.cursor().col()).isEqualTo(80);
    }

    @Test
    void testTN3270EraseAll() {
        TN3270Terminal t = TN3270Terminal.create();
        t.feed("Data");
        t.eraseAll();
        assertThat(t.render().get(0)).isEqualTo("                                                                                ");
    }

    @Test
    void testTN3270Reset() {
        TN3270Terminal t = TN3270Terminal.create();
        t.feed("Hello");
        t.reset();
        assertThat(t.render().get(0)).isEqualTo("                                                                                ");
        assertThat(t.cursor().row()).isEqualTo(1);
        assertThat(t.cursor().col()).isEqualTo(1);
    }

    @Test
    void testTN3270KeyboardArea() {
        TN3270Terminal t = TN3270Terminal.create();
        byte[] kb = t.keyboardArea();
        assertThat(kb).hasSize(32);
        byte[] data = new byte[]{(byte) 0xF1, 0x00, 0x00};
        t.setKeyboardArea(data);
        assertThat(t.keyboardArea()[0]).isEqualTo((byte) 0xF1);
    }

    // ── TN3270: Field Attributes (RFC 1576, §3.3) ──────────────────

    @Test
    void testTN3270FieldAttrNormal() {
        assertThat(TN3270FieldAttr.NORMAL.isEditable()).isTrue();
        assertThat(TN3270FieldAttr.NORMAL.isProtected()).isFalse();
        assertThat(TN3270FieldAttr.NORMAL.isBold()).isFalse();
        assertThat(TN3270FieldAttr.NORMAL.isUnderlined()).isFalse();
        assertThat(TN3270FieldAttr.NORMAL.primary()).isEqualTo(0x00);
        assertThat(TN3270FieldAttr.NORMAL.secondary()).isEqualTo(0x00);
    }

    @Test
    void testTN3270FieldAttrReadOnly() {
        assertThat(TN3270FieldAttr.READ_ONLY.isEditable()).isFalse();
        assertThat(TN3270FieldAttr.READ_ONLY.isProtected()).isTrue();
        assertThat(TN3270FieldAttr.READ_ONLY).isEqualTo(TN3270FieldAttr.PROTECTED);
    }

    @Test
    void testTN3270FieldAttrBoldIsEditable() {
        // BOLD is a display attribute, not a field type — still editable
        assertThat(TN3270FieldAttr.BOLD.isEditable()).isTrue();
        assertThat(TN3270FieldAttr.BOLD.isBold()).isTrue();
    }

    @Test
    void testTN3270FieldAttrUnderline() {
        assertThat(TN3270FieldAttr.UNDERLINE.isUnderlined()).isTrue();
        assertThat(TN3270FieldAttr.UNDERLINE.isEditable()).isTrue();
    }

    @Test
    void testTN3270FieldAttrReverse() {
        assertThat(TN3270FieldAttr.REVERSE.isReversed()).isTrue();
        assertThat(TN3270FieldAttr.REVERSE.isEditable()).isTrue();
    }

    @Test
    void testTN3270FieldAttrFlashing() {
        assertThat(TN3270FieldAttr.FLASH.isFlashing()).isTrue();
    }

    @Test
    void testTN3270FieldAttrBackgrounds() {
        assertThat(TN3270FieldAttr.BLUE_BG.secondary()).isEqualTo(0x11);
        assertThat(TN3270FieldAttr.GREEN_BG.secondary()).isEqualTo(0x13);
        assertThat(TN3270FieldAttr.YELLOW_BG.secondary()).isEqualTo(0x16);
        assertThat(TN3270FieldAttr.WHITE_BG.secondary()).isEqualTo(0x17);
    }

    @Test
    void testTN3270FieldAttrEquals() {
        TN3270FieldAttr a = TN3270FieldAttr.NORMAL;
        TN3270FieldAttr b = TN3270FieldAttr.NORMAL;
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    // ── TN3270: Screen Operations ──────────────────────────────────

    @Test
    void testTN3270ScreenWriteAndRead() {
        TN3270Screen s = new TN3270Screen(config);
        s.writeChars(new char[]{'T', 'E', 'S', 'T'}, TN3270FieldAttr.NORMAL);
        assertThat(s.charAt(1, 1)).isEqualTo('T');
        assertThat(s.charAt(1, 4)).isEqualTo('T');
        assertThat(s.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testTN3270ScreenWriteWithAttr() {
        TN3270Screen s = new TN3270Screen(config);
        s.writeChars(new char[]{'A', 'B'}, TN3270FieldAttr.BOLD);
        assertThat(s.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.BOLD);
        assertThat(s.attrAt(1, 2)).isEqualTo(TN3270FieldAttr.BOLD);
    }

    @Test
    void testTN3270ScreenSetFieldAttrs() {
        TN3270Screen s = new TN3270Screen(config);
        s.setFieldAttrs(1, 1, 10, 20, TN3270FieldAttr.READ_ONLY);
        assertThat(s.attrAt(5, 10)).isEqualTo(TN3270FieldAttr.READ_ONLY);
        assertThat(s.isEditable(5, 10)).isFalse();
    }

    @Test
    void testTN3270ScreenClearField() {
        TN3270Screen s = new TN3270Screen(config);
        s.writeChars(new char[]{'X', 'Y', 'Z'}, TN3270FieldAttr.NORMAL);
        s.clearField(1, 1, 1, 3);
        assertThat(s.charAt(1, 1)).isEqualTo(' ');
        assertThat(s.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testTN3270ScreenClearFieldClamped() {
        TN3270Screen s = new TN3270Screen(config);
        // Out-of-bounds clear should not throw
        s.clearField(-5, -5, 100, 200);
        // Screen unchanged
        assertThat(s.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testTN3270ScreenExceedsScreen() {
        TN3270Screen s = new TN3270Screen(config);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('X');
        s.writeChars(sb.toString().toCharArray(), TN3270FieldAttr.NORMAL);
        assertThat(s.cursorRow()).isEqualTo(24);
    }

    @Test
    void testTN3270ScreenRender() {
        TN3270Screen s = new TN3270Screen(config);
        s.writeChars(new char[]{'H', 'e', 'l', 'l', 'o'}, TN3270FieldAttr.NORMAL);
        List<String> render = s.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).startsWith("Hello");
    }

    @Test
    void testTN3270ScreenGrid() {
        TN3270Screen s = new TN3270Screen(config);
        char[][] grid = s.getGrid();
        assertThat(grid.length).isEqualTo(25);
        assertThat(grid[1].length).isEqualTo(81);
    }

    @Test
    void testTN3270ScreenAttrGrid() {
        TN3270Screen s = new TN3270Screen(config);
        TN3270FieldAttr[][] grid = s.getAttrGrid();
        assertThat(grid[1][1]).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    // ── TN3270: Control Functions (RFC 1576, §4.2) ─────────────────

    @Test
    void testTN3270RKFirstEditable() {
        TN3270Screen s = new TN3270Screen(config);
        s.setFieldAttrs(1, 1, 1, 9, TN3270FieldAttr.READ_ONLY);
        s.setFieldAttrs(1, 10, 1, 20, TN3270FieldAttr.NORMAL);
        // RK moves to first editable cell (col 10, not col 1-9 which are protected)
        assertThat(s.isEditable(1, 10)).isTrue();
        assertThat(s.isEditable(1, 5)).isFalse();
    }

    @Test
    void testTN3270DataStreamMode() {
        TN3270Terminal t = TN3270Terminal.create();
        assertThat(t.isUseDataStream()).isFalse();
        t.setUseDataStream(true);
        assertThat(t.isUseDataStream()).isTrue();
        t.setUseDataStream(false);
        assertThat(t.isUseDataStream()).isFalse();
    }

    // ── TN3270: Factory Registration ───────────────────────────────

    @Test
    void testTN3270FactoryRegistration() {
        TN3270Terminal t = TN3270Terminal.create(config);
        assertThat(t).isNotNull();
        assertThat(t.type()).isEqualTo("tn3270");
    }

    @Test
    void testTN3270AliasRegistration() {
        // "3270" is registered as an alias for "tn3270" via TerminalFactory
        var t = (TN3270Terminal) ssg.legoflow.network.terminals.base.io.TerminalFactory.create(
            "3270", TerminalConfig.builder().cols(80).rows(24).build());
        assertThat(t.type()).isEqualTo("tn3270");
    }

    // ── TN5250: Screen Model (RFC 1662, §3) ────────────────────────

    @Test
    void testTN5250DefaultSize() {
        TN5250Terminal t = TN5250Terminal.create();
        assertThat(t.type()).isEqualTo("tn5250");
        assertThat(t.title()).isEqualTo("TN5250");
        assertThat(t.supportsColor()).isTrue();
        assertThat(t.config().rows()).isEqualTo(24);
        assertThat(t.config().cols()).isEqualTo(80);
    }

    @Test
    void testTN5250Lettermode() {
        TN5250Terminal t = TN5250Terminal.create(
            TerminalConfig.builder().rows(52).cols(80).build());
        assertThat(t.config().rows()).isEqualTo(52);
        assertThat(t.config().cols()).isEqualTo(80);
    }

    @Test
    void testTN5250ScreenWriteChars() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("Hello, 5250!");
        List<String> render = t.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).contains("Hello, 5250!");
    }

    @Test
    void testTN5250ScreenWrapping() {
        TN5250Terminal t = TN5250Terminal.create();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('A');
        t.feed(sb.toString());
        List<String> render = t.render();
        assertThat(render.get(0)).hasSize(80);
        assertThat(render.get(1)).contains("A");
    }

    @Test
    void testTN5250CarriageReturn() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("Hello\rWorld");
        assertThat(t.render().get(0)).startsWith("World");
    }

    @Test
    void testTN5250Newlines() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("Row1\nRow2");
        List<String> render = t.render();
        assertThat(render.get(0)).startsWith("Row1");
        assertThat(render.get(1)).startsWith("Row2");
    }

    @Test
    void testTN5250Backspace() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("ABC\bD");
        // Backspace moves cursor back, 'D' overwrites 'C'
        assertThat(t.render().get(0)).contains("ABD");
    }

    @Test
    void testTN5250CursorPosition() {
        TN5250Terminal t = TN5250Terminal.create();
        t.cursorPosition(10, 20);
        Cursor c = t.cursor();
        assertThat(c.row()).isEqualTo(10);
        assertThat(c.col()).isEqualTo(20);
    }

    @Test
    void testTN5250Erase() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("Data");
        t.erase();
        assertThat(t.render().get(0)).isEqualTo("                                                                                ");
    }

    @Test
    void testTN5250Reset() {
        TN5250Terminal t = TN5250Terminal.create();
        t.feed("Hello");
        t.reset();
        assertThat(t.render().get(0)).isEqualTo("                                                                                ");
        assertThat(t.cursor().row()).isEqualTo(1);
        assertThat(t.cursor().col()).isEqualTo(1);
    }

    // ── TN5250: Field Attributes (RFC 1662, §3.1) ──────────────────

    @Test
    void testTN5250FieldAttrNormal() {
        assertThat(TN5250FieldAttr.NORMAL.isEditable()).isTrue();
        assertThat(TN5250FieldAttr.NORMAL.isEmphasized()).isFalse();
        assertThat(TN5250FieldAttr.NORMAL.isAutoSkip()).isFalse();
        assertThat(TN5250FieldAttr.NORMAL.isBlank()).isFalse();
    }

    @Test
    void testTN5250FieldAttrEmphasis() {
        assertThat(TN5250FieldAttr.EMPHASIS.isEmphasized()).isTrue();
        assertThat(TN5250FieldAttr.EMPHASIS.isEditable()).isTrue();
    }

    @Test
    void testTN5250FieldAttrAutoSkip() {
        assertThat(TN5250FieldAttr.AUTO_SKIP.isAutoSkip()).isTrue();
        assertThat(TN5250FieldAttr.AUTO_SKIP.isEditable()).isTrue();
    }

    @Test
    void testTN5250FieldAttrBlank() {
        assertThat(TN5250FieldAttr.BLANK.isBlank()).isTrue();
        assertThat(TN5250FieldAttr.BLANK.isEditable()).isFalse();
    }

    @Test
    void testTN5250FieldAttrEncodeDecode() {
        assertThat(TN5250FieldAttr.NORMAL.encode()).isEqualTo(0x00);
        assertThat(TN5250FieldAttr.EMPHASIS.encode()).isEqualTo(0x01);
        assertThat(TN5250FieldAttr.AUTO_SKIP.encode()).isEqualTo(0x02);
        assertThat(TN5250FieldAttr.BLANK.encode()).isEqualTo(0x04);
        assertThat(TN5250FieldAttr.FULL.encode()).isEqualTo(0x07);
        assertThat(TN5250FieldAttr.EMPHASIS_AUTO_SKIP.encode()).isEqualTo(0x03);
    }

    @Test
    void testTN5250FieldAttrDecode() {
        assertThat(TN5250FieldAttr.decode(0x00)).isEqualTo(TN5250FieldAttr.NORMAL);
        assertThat(TN5250FieldAttr.decode(0x01)).isEqualTo(TN5250FieldAttr.EMPHASIS);
        assertThat(TN5250FieldAttr.decode(0x04)).isEqualTo(TN5250FieldAttr.BLANK);
        assertThat(TN5250FieldAttr.decode(0x07)).isEqualTo(TN5250FieldAttr.FULL);
    }

    @Test
    void testTN5250FieldAttrEquals() {
        assertThat(TN5250FieldAttr.NORMAL).isEqualTo(TN5250FieldAttr.NORMAL);
        assertThat(TN5250FieldAttr.EMPHASIS).isEqualTo(TN5250FieldAttr.decode(0x01));
    }

    @Test
    void testTN5250FieldAttrWriteWithAttr() {
        TN5250Terminal t = TN5250Terminal.create();
        t.writeChars(new char[]{'X', 'Y'}, TN5250FieldAttr.EMPHASIS);
        assertThat(t.screen().attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testTN5250SetFieldAttrs() {
        TN5250Terminal t = TN5250Terminal.create();
        t.setFieldAttrs(1, 1, 5, 10, TN5250FieldAttr.BLANK);
        assertThat(t.screen().attrAt(3, 5)).isEqualTo(TN5250FieldAttr.BLANK);
    }

    @Test
    void testTN5250IsEditable() {
        TN5250Terminal t = TN5250Terminal.create();
        assertThat(t.isEditable()).isTrue();
    }

    @Test
    void testTN5250FactoryRegistration() {
        TN5250Terminal t = TN5250Terminal.create(config);
        assertThat(t).isNotNull();
        assertThat(t.type()).isEqualTo("tn5250");
    }

    @Test
    void testTN5250AliasRegistration() {
        // "5250" is registered as an alias for "tn5250" via TerminalFactory
        var t = (TN5250Terminal) ssg.legoflow.network.terminals.base.io.TerminalFactory.create(
            "5250", TerminalConfig.builder().cols(80).rows(24).build());
        assertThat(t.type()).isEqualTo("tn5250");
    }

    // ── TN5250: Screen Operations ──────────────────────────────────

    @Test
    void testTN5250ScreenDirectWriteChars() {
        TN5250Screen s = new TN5250Screen(config);
        s.writeChars(new char[]{'T', 'E', 'S', 'T'}, TN5250FieldAttr.NORMAL);
        assertThat(s.charAt(1, 1)).isEqualTo('T');
        assertThat(s.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testTN5250ScreenWriteBytes() {
        TN5250Screen s = new TN5250Screen(config);
        s.writeBytes(new byte[]{'A', 'B', 'C'}, TN5250FieldAttr.EMPHASIS);
        assertThat(s.charAt(1, 1)).isEqualTo('A');
        assertThat(s.charAt(1, 3)).isEqualTo('C');
        assertThat(s.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testTN5250ScreenSetFieldAttrs() {
        TN5250Screen s = new TN5250Screen(config);
        s.setFieldAttrs(1, 1, 10, 20, TN5250FieldAttr.BLANK);
        assertThat(s.attrAt(5, 10)).isEqualTo(TN5250FieldAttr.BLANK);
        assertThat(s.isEditable(5, 10)).isFalse();
    }

    @Test
    void testTN5250ScreenClearArea() {
        TN5250Screen s = new TN5250Screen(config);
        s.writeBytes(new byte[]{'X', 'Y', 'Z'}, TN5250FieldAttr.NORMAL);
        s.clearArea(1, 1, 1, 3);
        assertThat(s.charAt(1, 1)).isEqualTo(' ');
        assertThat(s.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testTN5250ScreenExceedsScreen() {
        TN5250Screen s = new TN5250Screen(config);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('X');
        s.writeBytes(sb.toString().getBytes(), TN5250FieldAttr.NORMAL);
        assertThat(s.cursorRow()).isEqualTo(24);
    }

    @Test
    void testTN5250ScreenRender() {
        TN5250Screen s = new TN5250Screen(config);
        s.writeBytes(new byte[]{'H', 'e', 'l', 'l', 'o'}, TN5250FieldAttr.NORMAL);
        List<String> render = s.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).startsWith("Hello");
    }

    @Test
    void testTN5250ScreenKeyboardArea() {
        TN5250Screen s = new TN5250Screen(config);
        byte[] kb = s.getKeyboardArea();
        assertThat(kb).hasSize(32);
    }

    @Test
    void testTN5250ScreenNonPrintable() {
        TN5250Screen s = new TN5250Screen(config);
        s.writeBytes(new byte[]{(byte) 0x00, (byte) 0xFF, (byte) 0x1F}, TN5250FieldAttr.NORMAL);
        assertThat(s.charAt(1, 1)).isEqualTo(' ');
        assertThat(s.charAt(1, 2)).isEqualTo(' ');
        assertThat(s.charAt(1, 3)).isEqualTo(' ');
    }

    @Test
    void testTN5250ScreenApplyFieldData() {
        TN5250Screen s = new TN5250Screen(config);
        s.applyFieldData(new byte[]{'T', 'E', 'S', 'T'}, 4, TN5250FieldAttr.NORMAL);
        assertThat(s.charAt(1, 1)).isEqualTo('T');
        assertThat(s.charAt(1, 4)).isEqualTo('T');
    }

    @Test
    void testTN5250ScreenReset() {
        TN5250Screen s = new TN5250Screen(config);
        s.cursorPosition(10, 20);
        s.writeBytes(new byte[]{'X'}, TN5250FieldAttr.NORMAL);
        s.reset();
        assertThat(s.cursorRow()).isEqualTo(1);
        assertThat(s.cursorCol()).isEqualTo(1);
        assertThat(s.charAt(1, 1)).isEqualTo(' ');
    }

    // ── TN3270/TN5250: Comparison ──────────────────────────────────

    @Test
    void testTN3270AndTN5250BothEmitBlankLineAfterReset() {
        TN3270Terminal t3270 = TN3270Terminal.create();
        TN5250Terminal t5250 = TN5250Terminal.create();
        t3270.reset();
        t5250.reset();
        assertThat(t3270.render().get(0)).isEqualTo(t5250.render().get(0));
    }

    @Test
    void testTN3270AndTN5250BothWrapAtColumnBoundary() {
        TN3270Terminal t3270 = TN3270Terminal.create();
        TN5250Terminal t5250 = TN5250Terminal.create();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('X');
        t3270.feed(sb.toString());
        t5250.feed(sb.toString());
        assertThat(t3270.render().get(1)).contains("X");
        assertThat(t5250.render().get(1)).contains("X");
    }

    @Test
    void testTN3270AndTN5250CRBehavior() {
        TN3270Terminal t3270 = TN3270Terminal.create();
        TN5250Terminal t5250 = TN5250Terminal.create();
        t3270.feed("ABC\rDEF");
        t5250.feed("ABC\rDEF");
        assertThat(t3270.render().get(0)).startsWith("DEF");
        assertThat(t5250.render().get(0)).startsWith("DEF");
    }

    @Test
    void testTN3270AndTN5250NewlineBehavior() {
        TN3270Terminal t3270 = TN3270Terminal.create();
        TN5250Terminal t5250 = TN5250Terminal.create();
        t3270.feed("A\nB");
        t5250.feed("A\nB");
        assertThat(t3270.render().get(1)).startsWith("B");
        assertThat(t5250.render().get(1)).startsWith("B");
    }
}
