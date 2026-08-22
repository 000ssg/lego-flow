package ssg.legoflow.network.terminals.tn5250;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
/**
 * Comprehensive tests for TN5250 terminal emulator.
 */
class TN5250TerminalTest {

    private TN5250Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = TN5250Terminal.create();
    }

    @Test
    void testCreateWithDefaultConfig() {
        assertThat(terminal).isNotNull();
        assertThat(terminal.type()).isEqualTo("tn5250");
        assertThat(terminal.title()).isEqualTo("TN5250");
        assertThat(terminal.supportsColor()).isTrue();
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    @Test
    void testCreateWithCustomConfig() {
        TerminalConfig config = TerminalConfig.builder()
            .rows(52).cols(80).build();
        TN5250Terminal t = TN5250Terminal.create(config);
        assertThat(t.config().rows()).isEqualTo(52);
        assertThat(t.config().cols()).isEqualTo(80);
    }

    @Test
    void testCreateWithoutArgs() {
        TN5250Terminal t = TN5250Terminal.create();
        assertThat(t).isNotNull();
        assertThat(t.config().rows()).isEqualTo(TN5250Terminal.DEFAULT_ROWS);
        assertThat(t.config().cols()).isEqualTo(TN5250Terminal.DEFAULT_COLS);
    }

    @Test
    void testNullConfigThrows() {
        assertThatThrownBy(() -> TN5250Terminal.create((TerminalConfig) null))
            .isExactlyInstanceOf(NullPointerException.class);
    }

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
        terminal.feed("Hello, 5250!");
        List<String> render = terminal.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).contains("Hello, 5250!");
    }

    @Test
    void testFeedStringNewlines() {
        terminal.feed("Row1\nRow2\nRow3");
        List<String> render = terminal.render();
        assertThat(render.get(0)).startsWith("Row1");
        assertThat(render.get(1)).startsWith("Row2");
        assertThat(render.get(2)).startsWith("Row3");
    }

    @Test
    void testFeedBytes() {
        terminal.feed(new byte[]{'A', 'B', 'C'});
        assertThat(terminal.render().get(0)).contains("ABC");
    }

    @Test
    void testFeedEmpty() {
        terminal.feed(new byte[0]);
        terminal.feed("");
        assertThat(terminal.render()).hasSize(24);
    }

    @Test
    void testFeedChar() {
        terminal.feedChar('X');
        assertThat(terminal.render().get(0)).startsWith("X");
    }

    @Test
    void testCursorPosition() {
        terminal.cursorPosition(10, 20);
        assertThat(terminal.cursorRow()).isEqualTo(10);
        assertThat(terminal.cursorCol()).isEqualTo(20);
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
        terminal.cursorPosition(1, 1);
        assertThat(terminal.currentAttr()).isNotNull();
    }

    @Test
    void testConfig() {
        assertThat(terminal.config()).isNotNull();
        assertThat(terminal.config().rows()).isEqualTo(24);
    }

    @Test
    void testListeners() {
        terminal.addEventListener(e -> {});
        terminal.feed("test");
        assertThat(terminal.render().get(0)).contains("test");
    }

    @Test
    void testRemoveListener() {
        terminal.addEventListener(t -> {});
        terminal.removeEventListener(t -> {});
        terminal.feed("test");
        assertThat(terminal.render().get(0)).contains("test");
    }

    @Test
    void testReset() {
        terminal.feed("Hello");
        terminal.reset();
        assertThat(terminal.render().get(0)).isEqualTo("                                                                                ");
        assertThat(terminal.cursorRow()).isEqualTo(1);
        assertThat(terminal.cursorCol()).isEqualTo(1);
    }

    @Test
    void testErase() {
        terminal.feed("Data");
        terminal.erase();
        assertThat(terminal.render().get(0)).isEqualTo("                                                                                ");
    }

    @Test
    void testWriteString() {
        terminal.writeString("Test");
        assertThat(terminal.render().get(0)).contains("Test");
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
    void testWriteCharsWithAttr() {
        terminal.writeChars(new char[]{'A', 'B'}, TN5250FieldAttr.EMPHASIS);
        assertThat(terminal.screen().attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testSetFieldAttrs() {
        terminal.setFieldAttrs(1, 1, 5, 10, TN5250FieldAttr.BLANK);
        assertThat(terminal.screen().attrAt(3, 5)).isEqualTo(TN5250FieldAttr.BLANK);
    }

    @Test
    void testFactoryRegistration() {
        Terminal tn5250 = TerminalFactory.create("tn5250", TerminalConfig.builder().rows(24).cols(80).build());
        assertThat(tn5250).isInstanceOf(TN5250Terminal.class);
        assertThat(tn5250.type()).isEqualTo("tn5250");
    }

    @Test
    void testAliasRegistration() {
        Terminal tn5250 = TerminalFactory.create("5250", TerminalConfig.builder().rows(24).cols(80).build());
        assertThat(tn5250).isInstanceOf(TN5250Terminal.class);
    }

    @Test
    void testLongTextWraps() {
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
        // Backspace moves cursor back, next char overwrites — result is "ABD"
        assertThat(terminal.render().get(0)).contains("ABD");
    }

    @Test
    void testTerminatesWithNewline() {
        terminal.feed("ABC\nDEF");
        assertThat(terminal.render().get(0)).contains("ABC");
        assertThat(terminal.render().get(1)).contains("DEF");
    }
}
