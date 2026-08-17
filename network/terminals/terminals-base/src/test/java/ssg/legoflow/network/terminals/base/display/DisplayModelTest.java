package ssg.legoflow.network.terminals.base.display;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.assertThat;

class DisplayModelTest {

    private DisplayModel dm;

    @BeforeEach
    void setUp() {
        dm = new DisplayModel(TerminalConfig.builder().rows(24).cols(80).build());
    }

    @Test
    void testPutChar() {
        dm.putChar('H');
        String line = dm.screen().rowString(1);
        assertThat(line).startsWith("H");
    }

    @Test
    void testCursorPosition() {
        dm.cursorPosition(5, 10);
        assertThat(dm.cursor().row()).isEqualTo(5);
        assertThat(dm.cursor().col()).isEqualTo(10);
    }

    @Test
    void testCursorUpClamped() {
        dm.cursorPosition(3, 1);
        dm.cursorUp(10);
        assertThat(dm.cursor().row()).isEqualTo(1);
    }

    @Test
    void testCursorDownClamped() {
        dm.cursorPosition(22, 1);
        dm.cursorDown(10);
        assertThat(dm.cursor().row()).isEqualTo(24);
    }

    @Test
    void testClear() {
        dm.putChar('X');
        dm.clear();
        assertThat(dm.screen().at(1, 1).codepoint()).isEqualTo(' ');
    }

    @Test
    void testTitle() {
        dm.setTitle("My Terminal");
        assertThat(dm.title()).isEqualTo("My Terminal");
    }

    @Test
    void testRender() {
        dm.putChar('A');
        var lines = dm.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).startsWith("A");
    }

    @Test
    void testEraseDisplayMode0() {
        dm.cursorPosition(2, 3);
        dm.eraseDisplay(0);
        // From cursor to end should be spaces
        assertThat(dm.screen().at(2, 3).codepoint()).isEqualTo(' ');
        assertThat(dm.screen().at(2, 5).codepoint()).isEqualTo(' ');
    }

    @Test
    void testEraseLineMode2() {
        // Put some content
        dm.cursorPosition(1, 1);
        for (int i = 0; i < 5; i++) dm.putChar((char)('A' + i));
        dm.cursorPosition(1, 3);
        dm.eraseLine(2);
        // Entire line should be spaces
        for (int c = 1; c <= 5; c++) {
            assertThat(dm.screen().at(1, c).codepoint()).isEqualTo(' ');
        }
    }

    @Test
    void testOriginModeCursorPosition() {
        TerminalConfig config = TerminalConfig.builder().rows(24).cols(80).originMode(true).build();
        DisplayModel model = new DisplayModel(config);
        model.screen().setScrollRegion(5, 20);
        model.cursorPosition(1, 1);
        // With origin mode, position 1,1 maps to scroll region top-left
        assertThat(model.cursor().row()).isEqualTo(5);
    }
}
