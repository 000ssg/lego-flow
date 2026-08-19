package ssg.legoflow.network.terminals.tn3270;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive tests for TN3270 terminal emulator.
 */
class TN3270TerminalTest {

    private TN3270Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = TN3270Terminal.create();
    }

    @Test
    void testCreateWithDefaultConfig() {
        assertThat(terminal).isNotNull();
        assertThat(terminal.type()).isEqualTo("tn3270");
        assertThat(terminal.title()).isEqualTo("TN3270");
        assertThat(terminal.supportsColor()).isTrue();
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    @Test
    void testCreateWithCustomConfig() {
        TerminalConfig config = TerminalConfig.builder()
            .rows(43).cols(132).build();
        TN3270Terminal t = TN3270Terminal.create(config);
        assertThat(t.config().rows()).isEqualTo(43);
        assertThat(t.config().cols()).isEqualTo(132);
        assertThat(t.type()).isEqualTo("tn3270");
    }

    @Test
    void testCreateWithoutArgs() {
        TN3270Terminal t = TN3270Terminal.create();
        assertThat(t).isNotNull();
        assertThat(t.config().rows()).isEqualTo(TN3270Terminal.DEFAULT_ROWS);
        assertThat(t.config().cols()).isEqualTo(TN3270Terminal.DEFAULT_COLS);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> TN3270Terminal.create((TerminalConfig) null))
            .isExactlyInstanceOf(NullPointerException.class);
    }

    // --- Terminal Interface ---

    @Test
    void testFeedNullDataThrows() {
        assertThatThrownBy(() -> terminal.feed((byte[]) null))
            .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void testFeedNullStringThrows() {
        assertThatThrownBy(() -> terminal.feed((String) null))
            .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void testFeedStringPrintable() {
        terminal.feed("Hello, 3270!");
        List<String> render = terminal.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).contains("Hello, 3270!");
        // 12 chars written → cursor at col 13
        assertThat(terminal.cursorCol()).isEqualTo(13);
        assertThat(terminal.cursorRow()).isEqualTo(1);
    }

    @Test
    void testFeedStringNewlines() {
        terminal.feed("Row1\nRow2\nRow3");
        List<String> render = terminal.render();
        assertThat(render.get(0)).startsWith("Row1");
        assertThat(render.get(1)).startsWith("Row2");
        assertThat(render.get(2)).startsWith("Row3");
        // "Row3" is 4 chars → cursor at col 5
        assertThat(terminal.cursorRow()).isEqualTo(3);
        assertThat(terminal.cursorCol()).isEqualTo(5);
    }

    @Test
    void testFeedStringCarriageReturn() {
        terminal.feed("Hello\rWorld");
        List<String> render = terminal.render();
        assertThat(render.get(0)).startsWith("World");
        // "World" is 5 chars → cursor at col 6
        assertThat(terminal.cursorCol()).isEqualTo(6);
    }

    @Test
    void testFeedBytesPrintable() {
        terminal.feed(new byte[]{'A', 'B', 'C'});
        List<String> render = terminal.render();
        assertThat(render.get(0)).startsWith("ABC");
    }

    @Test
    void testFeedEmpty() {
        terminal.feed(new byte[0]);
        terminal.feed("");
        List<String> render = terminal.render();
        assertThat(render).hasSize(24);
    }

    @Test
    void testFeedChar() {
        terminal.feedChar('X');
        List<String> render = terminal.render();
        assertThat(render.get(0)).startsWith("X");
    }

    @Test
    void testCursorPosition() {
        terminal.cursorPosition(5, 10);
        assertThat(terminal.cursorRow()).isEqualTo(5);
        assertThat(terminal.cursorCol()).isEqualTo(10);
    }

    @Test
    void testCursorPositionClamped() {
        terminal.cursorPosition(0, 0);
        assertThat(terminal.cursorRow()).isEqualTo(1);
        assertThat(terminal.cursorCol()).isEqualTo(1);

        terminal.cursorPosition(100, 200);
        assertThat(terminal.cursorRow()).isEqualTo(24);
        assertThat(terminal.cursorCol()).isEqualTo(80);
    }

    @Test
    void testCursor() {
        terminal.cursorPosition(10, 20);
        Cursor cursor = terminal.cursor();
        assertThat(cursor.row()).isEqualTo(10);
        assertThat(cursor.col()).isEqualTo(20);
    }

    @Test
    void testCurrentAttr() {
        TN3270FieldAttr attr = TN3270FieldAttr.NORMAL;
        terminal.screen().setFieldAttrs(1, 1, 1, 80, attr);
        terminal.cursorPosition(1, 1);
        assertThat(terminal.currentAttr()).isNotNull();
    }

    @Test
    void testConfig() {
        assertThat(terminal.config()).isNotNull();
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    @Test
    void testListeners() {
        terminal.addEventListener(e -> {});
        terminal.feed("test");
        assertThat(terminal.render().get(0)).contains("test");
    }

    @Test
    void testRemoveListener() {
        terminal.addEventListener(e -> {});
        terminal.removeEventListener(e -> {});
        terminal.feed("test");
        assertThat(terminal.render().get(0)).contains("test");
    }

    @Test
    void testReset() {
        terminal.feed("Hello");
        terminal.reset();
        List<String> render = terminal.render();
        assertThat(render.get(0)).isEqualTo("                                                                                ");
        assertThat(terminal.cursorRow()).isEqualTo(1);
        assertThat(terminal.cursorCol()).isEqualTo(1);
    }

    @Test
    void testEraseAll() {
        terminal.feed("Data");
        terminal.eraseAll();
        List<String> render = terminal.render();
        assertThat(render.get(0)).isEqualTo("                                                                                ");
    }

    @Test
    void testWriteString() {
        terminal.writeString("Test");
        assertThat(terminal.render().get(0)).contains("Test");
    }

    @Test
    void testDataStreamMode() {
        // Default is raw mode for character-by-character input
        assertThat(terminal.isUseDataStream()).isFalse();
        terminal.setUseDataStream(true);
        assertThat(terminal.isUseDataStream()).isTrue();
        terminal.setUseDataStream(false);
        assertThat(terminal.isUseDataStream()).isFalse();
    }

    @Test
    void testScreen() {
        assertThat(terminal.screen()).isNotNull();
        assertThat(terminal.screen().rows()).isEqualTo(24);
        assertThat(terminal.screen().cols()).isEqualTo(80);
    }

    @Test
    void testKeyboardArea() {
        byte[] kb = terminal.keyboardArea();
        assertThat(kb).hasSize(32);
        assertThat(kb).contains((byte) 0);
    }

    @Test
    void testSetKeyboardArea() {
        byte[] data = new byte[32];
        for (int i = 0; i < 32; i++) data[i] = (byte) (i + 1);
        terminal.setKeyboardArea(data);
        byte[] kb = terminal.keyboardArea();
        for (int i = 0; i < 32; i++) assertThat(kb[i]).isEqualTo((byte) (i + 1));
    }

    @Test
    void testIsEditable() {
        terminal.cursorPosition(1, 1);
        assertThat(terminal.isEditable()).isTrue();
    }

    @Test
    void testScreenWriteBytes() {
        terminal.screen().writeBytes(new byte[]{'X', 'Y'}, TN3270FieldAttr.NORMAL);
        assertThat(terminal.screen().charAt(1, 1)).isEqualTo('X');
        assertThat(terminal.screen().charAt(1, 2)).isEqualTo('Y');
    }

    @Test
    void testScreenWriteChars() {
        terminal.screen().writeChars(new char[]{'A', 'B', 'C'}, TN3270FieldAttr.BOLD);
        assertThat(terminal.screen().charAt(1, 1)).isEqualTo('A');
        assertThat(terminal.screen().charAt(1, 2)).isEqualTo('B');
        assertThat(terminal.screen().charAt(1, 3)).isEqualTo('C');
        assertThat(terminal.screen().attrAt(1, 1)).isEqualTo(TN3270FieldAttr.BOLD);
    }

    @Test
    void testRender() {
        terminal.feed("Line 1");
        List<String> render = terminal.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).contains("Line 1");
        assertThat(render.get(1)).isEqualTo("                                                                                ");
    }

    @Test
    void testLongTextWrapsAcrossLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('A');
        terminal.feed(sb.toString());
        List<String> render = terminal.render();
        assertThat(render.get(0)).hasSize(80);
        assertThat(render.get(1)).contains("A");
    }

    @Test
    void testBackspace() {
        terminal.feed("ABC\bD");
        List<String> render = terminal.render();
        // Backspace moves cursor back, next char overwrites — result is "ABD"
        assertThat(render.get(0)).contains("ABD");
    }

    @Test
    void testTerminatesWithNewline() {
        terminal.feed("ABC\nDEF");
        List<String> render = terminal.render();
        assertThat(render.get(0)).contains("ABC");
        assertThat(render.get(1)).contains("DEF");
    }

    // --- Registration Tests ---

    @Test
    void testFactoryRegistration() {
        Terminal tn3270 = TerminalFactory.create("tn3270", TerminalConfig.builder().rows(24).cols(80).build());
        assertThat(tn3270).isInstanceOf(TN3270Terminal.class);
        assertThat(tn3270.type()).isEqualTo("tn3270");
    }

    @Test
    void testAliasRegistration() {
        Terminal tn3270 = TerminalFactory.create("3270", TerminalConfig.builder().rows(24).cols(80).build());
        assertThat(tn3270).isInstanceOf(TN3270Terminal.class);
        assertThat(tn3270.type()).isEqualTo("tn3270");
    }

    // --- Field Attr Tests ---

    @Test
    void testFieldAttrFromScreen() {
        terminal.screen().setFieldAttrs(1, 1, 1, 10, TN3270FieldAttr.READ_ONLY);
        assertThat(terminal.screen().attrAt(1, 5)).isEqualTo(TN3270FieldAttr.READ_ONLY);
    }

    @Test
    void testScreenAttn() {
        assertThat(terminal.screen().charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testClearField() {
        terminal.feed("XXX");
        terminal.screen().clearField(1, 1, 1, 3);
        assertThat(terminal.render().get(0)).isEqualTo("                                                                                ");
    }
}
