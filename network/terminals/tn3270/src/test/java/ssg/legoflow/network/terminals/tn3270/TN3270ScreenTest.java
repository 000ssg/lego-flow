package ssg.legoflow.network.terminals.tn3270;

import org.junit.jupiter.api.*;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.tn3270.TN3270DataStreamParser.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for the TN3270 screen model.
 */
class TN3270ScreenTest {

    private TN3270Screen screen;

    @BeforeEach
    void setUp() {
        screen = new TN3270Screen(TerminalConfig.builder()
            .rows(24).cols(80).build());
    }

    @Test
    void testRowsCols() {
        assertThat(screen.rows()).isEqualTo(24);
        assertThat(screen.cols()).isEqualTo(80);
    }

    @Test
    void testCustomSize() {
        TN3270Screen s = new TN3270Screen(TerminalConfig.builder()
            .rows(43).cols(132).build());
        assertThat(s.rows()).isEqualTo(43);
        assertThat(s.cols()).isEqualTo(132);
    }

    @Test
    void testReset() {
        screen.cursorPosition(10, 20);
        screen.writeBytes(new byte[]{'X', 'Y'}, TN3270FieldAttr.NORMAL);
        screen.reset();
        assertThat(screen.cursorRow()).isEqualTo(1);
        assertThat(screen.cursorCol()).isEqualTo(1);
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.NORMAL);
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
        assertThat(screen.cursorCol()).isEqualTo(1);

        screen.cursorPosition(100, 200);
        assertThat(screen.cursorRow()).isEqualTo(24);
        assertThat(screen.cursorCol()).isEqualTo(80);
    }

    @Test
    void testWriteBytes() {
        screen.writeBytes(new byte[]{'A', 'B', 'C'}, TN3270FieldAttr.NORMAL);
        assertThat(screen.charAt(1, 1)).isEqualTo('A');
        assertThat(screen.charAt(1, 2)).isEqualTo('B');
        assertThat(screen.charAt(1, 3)).isEqualTo('C');
        assertThat(screen.cursorCol()).isEqualTo(4);
    }

    @Test
    void testWriteChars() {
        screen.writeChars(new char[]{'X', 'Y'}, TN3270FieldAttr.BOLD);
        assertThat(screen.charAt(1, 1)).isEqualTo('X');
        assertThat(screen.charAt(1, 2)).isEqualTo('Y');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.BOLD);
        assertThat(screen.attrAt(1, 2)).isEqualTo(TN3270FieldAttr.BOLD);
    }

    @Test
    void testWriteWrapsLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 90; i++) sb.append('A');
        screen.writeChars(sb.toString().toCharArray(), TN3270FieldAttr.NORMAL);
        // 80 on row 1, 10 on row 2 → cursor at col 11
        assertThat(screen.cursorRow()).isEqualTo(2);
        assertThat(screen.cursorCol()).isEqualTo(11);
    }

    @Test
    void testWriteExceedsScreen() {
        // 24*80=1920 cells, 2000 chars exceed it
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 2000; i++) sb.append('X');
        screen.writeChars(sb.toString().toCharArray(), TN3270FieldAttr.NORMAL);
        assertThat(screen.cursorRow()).isEqualTo(24);
    }

    @Test
    void testEraseAll() {
        screen.writeBytes(new byte[]{'X'}, TN3270FieldAttr.NORMAL);
        screen.eraseAll();
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testEraseUnchanged() {
        // Write with READ_ONLY → protected field
        screen.writeBytes(new byte[]{'Y'}, TN3270FieldAttr.READ_ONLY);
        screen.eraseUnchanged();
        // Protected fields: screen cleared, attribute preserved
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.READ_ONLY);
    }

    @Test
    void testSetFieldReadOnly() {
        screen.setFieldReadOnly(1, 1, 1, 10);
        for (int c = 1; c <= 10; c++) {
            assertThat(screen.attrAt(1, c)).isEqualTo(TN3270FieldAttr.READ_ONLY);
        }
    }

    @Test
    void testClearField() {
        screen.writeBytes(new byte[]{'A', 'B', 'C'}, TN3270FieldAttr.NORMAL);
        screen.clearField(1, 1, 1, 3);
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.attrAt(1, 1)).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testIsEditable() {
        screen.cursorPosition(1, 1);
        assertThat(screen.isEditable(1, 1)).isTrue();

        screen.setFieldReadOnly(1, 5, 1, 10);
        assertThat(screen.isEditable(1, 5)).isFalse();
    }

    @Test
    void testIsEditableOutOfBounds() {
        assertThat(screen.isEditable(0, 1)).isFalse();
        assertThat(screen.isEditable(1, 0)).isFalse();
        assertThat(screen.isEditable(25, 1)).isFalse();
        assertThat(screen.isEditable(1, 81)).isFalse();
    }

    @Test
    void testGetGrid() {
        char[][] grid = screen.getGrid();
        assertThat(grid.length).isEqualTo(25); // 0..24
        assertThat(grid[1].length).isEqualTo(81); // 0..80
    }

    @Test
    void testGetAttrGrid() {
        TN3270FieldAttr[][] grid = screen.getAttrGrid();
        assertThat(grid[1][1]).isEqualTo(TN3270FieldAttr.NORMAL);
    }

    @Test
    void testGetKeyboardArea() {
        byte[] kb = screen.getKeyboardArea();
        assertThat(kb).hasSize(32);
        assertThat(kb[0]).isEqualTo((byte) 0);
    }

    @Test
    void testSetKeyboardArea() {
        byte[] data = new byte[]{(byte) 1, (byte) 2, (byte) 3};
        screen.setKeyboardArea(data);
        assertThat(screen.getKeyboardArea()[0]).isEqualTo((byte) 1);
        assertThat(screen.getKeyboardArea()[1]).isEqualTo((byte) 2);
        assertThat(screen.getKeyboardArea()[2]).isEqualTo((byte) 3);
    }

    @Test
    void testRender() {
        screen.writeBytes(new byte[]{'H', 'e', 'l', 'l', 'o'}, TN3270FieldAttr.NORMAL);
        List<String> render = screen.render();
        assertThat(render).hasSize(24);
        assertThat(render.get(0)).startsWith("Hello");
    }

    @Test
    void testRenderToString() {
        screen.writeBytes(new byte[]{'T'}, TN3270FieldAttr.NORMAL);
        String s = screen.renderToString();
        assertThat(s).contains("T");
    }

    @Test
    void testApplyFieldData() {
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            new FieldDataRecord(new byte[]{'T', 'E', 'S', 'T'}, TN3270FieldAttr.NORMAL)
        ));
        assertThat(screen.charAt(1, 1)).isEqualTo('T');
        assertThat(screen.charAt(1, 4)).isEqualTo('T');
    }

    @Test
    void testApplyRTS() {
        screen.writeBytes(new byte[]{'X'}, TN3270FieldAttr.NORMAL);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.RTS
        ));
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testApplyTSS() {
        screen.writeBytes(new byte[]{'X'}, TN3270FieldAttr.NORMAL);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.TSS
        ));
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
        assertThat(screen.cursorRow()).isEqualTo(1);
        assertThat(screen.cursorCol()).isEqualTo(1);
    }

    @Test
    void testApplyECD() {
        screen.writeBytes(new byte[]{'X'}, TN3270FieldAttr.NORMAL);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.ECD
        ));
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testApplyUNDO() {
        screen.writeBytes(new byte[]{'X'}, TN3270FieldAttr.READ_ONLY);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.UNDO
        ));
        assertThat(screen.charAt(1, 1)).isEqualTo(' ');
    }

    @Test
    void testApplyPPI() {
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.PPI
        ));
        // PPI just marks end of stream, no screen change expected
    }

    @Test
    void testApplyFlash() {
        screen.setFieldAttrs(1, 1, 1, 5, TN3270FieldAttr.NORMAL);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.FLASH
        ));
        TN3270FieldAttr attr = screen.attrAt(1, 3);
        assertThat(attr.isFlashing()).isTrue();
    }

    @Test
    void testApplyRK() {
        screen.setFieldAttrs(1, 1, 1, 9, TN3270FieldAttr.READ_ONLY);
        screen.setFieldAttrs(1, 10, 1, 20, TN3270FieldAttr.NORMAL);
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.RK
        ));
        // RK moves to first editable cell (col 10)
        assertThat(screen.cursorRow()).isEqualTo(1);
        assertThat(screen.cursorCol()).isEqualTo(10);
    }

    @Test
    void testApplyATN() {
        // ATN is handled silently
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            ControlRecord.ATN
        ));
    }

    @Test
    void testApplyUnknownControl() {
        screen.applyDataStream(List.of(
            new KeyboardDataRecord(new byte[32]),
            new UnknownControlRecord(0xFF)
        ));
    }

    @Test
    void testTruncatedRecordIgnored() {
        byte[] data = new byte[32 + 3];
        for (int i = 0; i < 32; i++) data[i] = 0;
        data[32] = (byte) 0xFF;
        data[33] = 0x00;
        data[34] = 0x00;
        screen.applyDataStream(List.of(new FieldDataRecord(data, TN3270FieldAttr.NORMAL)));
        // Should not throw
    }
}
