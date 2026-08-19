package ssg.legoflow.network.terminals.tn5250;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the TN5250 screen model.
 */
class TN5250ScreenTest {

    private TN5250Screen screen;

    @BeforeEach
    void setUp() {
        screen = new TN5250Screen(TerminalConfig.builder()
            .rows(24).cols(80).build());
    }

    @Test
    void testRowsCols() {
        assertThat(screen.rows()).isEqualTo(24);
        assertThat(screen.cols()).isEqualTo(80);
    }

    @Test
    void testCustomSize() {
        TN5250Screen s = new TN5250Screen(TerminalConfig.builder()
            .rows(52).cols(80).build());
        assertThat(s.rows()).isEqualTo(52);
    }

    @Test
    void testReset() {
        screen.cursorPosition(10, 20);
        screen.writeBytes(new byte[]{'X'}, TN5250FieldAttr.NORMAL);
        screen.reset();
        assertThat(screen.cursorRow()).isEqualTo(1);
        assertThat(screen.cursorCol()).isEqualTo(1);
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testCursorPosition() {
        screen.cursorPosition(5, 10);
        assertThat(screen.cursorRow()).isEqualTo(5);
        assertThat(screen.cursorCol()).isEqualTo(10);
    }

    @Test
    void testCursorPositionClamped() {
        screen.cursorPosition(0, 0);
        assertThat(screen.cursorRow()).isEqualTo(1);
        screen.cursorPosition(100, 200);
        assertThat(screen.cursorRow()).isEqualTo(24);
        assertThat(screen.cursorCol()).isEqualTo(80);
    }

    @Test
    void testWriteBytes() {
        screen.writeBytes(new byte[]{'A', 'B', 'C'}, TN5250FieldAttr.NORMAL);
        assertThat(screen.charAt(1, 1)).isEqualTo('A');
        assertThat(screen.charAt(1, 2)).isEqualTo('B');
        assertThat(screen.charAt(1, 3)).isEqualTo('C');
    }

    @Test
    void testWriteChars() {
        screen.writeChars(new char[]{'X', 'Y'}, TN5250FieldAttr.EMPHASIS);
        assertThat(screen.charAt(1, 1)).isEqualTo('X');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testWriteBytesWrapsLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('A');
        screen.writeBytes(sb.toString().getBytes(), TN5250FieldAttr.NORMAL);
        assertThat(screen.cursorRow()).isEqualTo(2);
    }

    @Test
    void testWriteExceedsScreen() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('X');
        screen.writeBytes(sb.toString().getBytes(), TN5250FieldAttr.NORMAL);
        assertThat(screen.cursorRow()).isEqualTo(24);
    }

    @Test
    void testErase() {
        screen.writeBytes(new byte[]{'X'}, TN5250FieldAttr.NORMAL);
        screen.erase();
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testSetFieldAttrs() {
        screen.setFieldAttrs(1, 1, 10, 20, TN5250FieldAttr.BLANK);
        assertThat(screen.attrAt(5, 10)).isEqualTo(TN5250FieldAttr.BLANK);
    }

    @Test
    void testSetFieldAttrsClamped() {
        screen.setFieldAttrs(0, 0, 100, 200, TN5250FieldAttr.EMPHASIS);
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
        assertThat(screen.attrAt(24, 80)).isEqualTo(TN5250FieldAttr.EMPHASIS);
        assertThat(screen.attrAt(25, 80)).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testClearArea() {
        screen.writeBytes(new byte[]{'X', 'Y', 'Z'}, TN5250FieldAttr.NORMAL);
        screen.clearArea(1, 1, 1, 3);
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.attrAt(1, 2)).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testIsEditable() {
        assertThat(screen.isEditable(1, 1)).isTrue();
        screen.setFieldAttrs(1, 5, 1, 10, TN5250FieldAttr.BLANK);
        assertThat(screen.isEditable(1, 5)).isFalse();
    }

    @Test
    void testIsEditableOutOfBounds() {
        assertThat(screen.isEditable(0, 1)).isFalse();
        assertThat(screen.isEditable(25, 1)).isFalse();
    }

    @Test
    void testGetGrid() {
        char[][] grid = screen.getGrid();
        assertThat(grid.length).isEqualTo(25);
        assertThat(grid[1].length).isEqualTo(81);
    }

    @Test
    void testGetAttrGrid() {
        TN5250FieldAttr[][] grid = screen.getAttrGrid();
        assertThat(grid[1][1]).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testCursorAttr() {
        screen.cursorPosition(1, 1);
        assertThat(screen.cursorAttr()).isEqualTo(TN5250FieldAttr.NORMAL);
    }

    @Test
    void testGetKeyboardArea() {
        byte[] kb = screen.getKeyboardArea();
        assertThat(kb).hasSize(32);
    }

    @Test
    void testSetKeyboardArea() {
        byte[] data = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        screen.setKeyboardArea(data);
        assertThat(screen.getKeyboardArea()[0]).isEqualTo((byte) 1);
    }

    @Test
    void testApplyFieldData() {
        screen.applyFieldData(new byte[]{'T', 'E', 'S', 'T'}, 4, TN5250FieldAttr.NORMAL);
        assertThat(screen.charAt(1, 1)).isEqualTo('T');
        assertThat(screen.charAt(1, 4)).isEqualTo('T');
    }

    @Test
    void testApplyFieldDataWithAttr() {
        screen.applyFieldData(new byte[]{'X'}, 1, TN5250FieldAttr.EMPHASIS);
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN5250FieldAttr.EMPHASIS);
    }

    @Test
    void testRender() {
        screen.writeBytes(new byte[]{'H', 'e', 'l', 'l', 'o'}, TN5250FieldAttr.NORMAL);
        List<String> render = screen.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).startsWith("Hello");
    }

    @Test
    void testRenderToString() {
        screen.writeBytes(new byte[]{'T'}, TN5250FieldAttr.NORMAL);
        assertThat(screen.renderToString()).contains("T");
    }

    @Test
    void testNonPrintableBytesBecomeSpace() {
        screen.writeBytes(new byte[]{(byte) 0x00, (byte) 0xFF, (byte) 0x1F}, TN5250FieldAttr.NORMAL);
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.charAt(1, 2)).isEqualTo(' ');
        assertThat(screen.charAt(1, 3)).isEqualTo(' ');
    }

    @Test
    void testNormalAttrNotEmphasized() {
        assertThat(TN5250FieldAttr.NORMAL.isEmphasized()).isFalse();
    }

    @Test
    void testNormalAttrNotAutoSkip() {
        assertThat(TN5250FieldAttr.NORMAL.isAutoSkip()).isFalse();
    }

    @Test
    void testNormalAttrNotBlank() {
        assertThat(TN5250FieldAttr.NORMAL.isBlank()).isFalse();
    }
}
