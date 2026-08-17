package ssg.legoflow.network.terminals.base.display;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ScreenTest {

    private Screen createScreen() {
        return new Screen(24, 80, 64);
    }

    @Test
    void defaultCursorAtHome() {
        Screen s = createScreen();
        assertThat(s.cursor().row()).isEqualTo(1);
        assertThat(s.cursor().col()).isEqualTo(1);
    }

    @Test
    void putCharacter() {
        Screen s = createScreen();
        s.put(new Character('H', TermAttr.DEFAULT));
        Character cell = s.at(1, 1);
        assertThat(cell.codepoint()).isEqualTo('H');
        assertThat(s.cursor().col()).isEqualTo(2);
    }

    @Test
    void putAtRightEdgeSetsWrapPending() {
        Screen s = new Screen(2, 3, 0);
        for (int i = 0; i < 3; i++) {
            s.put(new Character('X', TermAttr.DEFAULT));
        }
        // After 3 chars in 3-col screen, cursor stays at col 3 with wrap pending
        assertThat(s.cursor().row()).isEqualTo(1);
        assertThat(s.cursor().col()).isEqualTo(3);
        assertThat(s.wrapPending()).isTrue();

        // Next character triggers the wrap: advances to row 2, writes there
        s.put(new Character('Y', TermAttr.DEFAULT));
        assertThat(s.cursor().row()).isEqualTo(2);
        assertThat(s.cursor().col()).isEqualTo(2);
        assertThat(s.wrapPending()).isFalse();
        assertThat(s.at(2, 1).codepoint()).isEqualTo('Y');
    }

    @Test
    void scrollDown() {
        Screen s = new Screen(3, 5, 10);
        // Fill all rows
        for (int r = 0; r < 3; r++) {
            s.cursor().setPos(r + 1, 1);
            s.put(new Character((char)('A' + r), TermAttr.DEFAULT));
        }
        s.scrollDown();
        // Row 1 should now be empty (top line scrolled out)
        assertThat(s.at(1, 1).codepoint()).isEqualTo(' ');
    }

    @Test
    void scrollUp() {
        Screen s = new Screen(3, 5, 10);
        for (int r = 0; r < 3; r++) {
            s.cursor().setPos(r + 1, 1);
            s.put(new Character((char)('A' + r), TermAttr.DEFAULT));
        }
        s.scrollUp();
        // Row 3 should now be empty
        assertThat(s.at(3, 1).codepoint()).isEqualTo(' ');
    }

    @Test
    void scrollRegion() {
        Screen s = createScreen();
        s.setScrollRegion(5, 20);
        assertThat(s.scrollTop()).isEqualTo(5);
        assertThat(s.scrollBottom()).isEqualTo(20);
    }

    @Test
    void insertLines() {
        Screen s = new Screen(3, 5, 0);
        s.setScrollRegion(1, 3);
        for (int i = 0; i < 3; i++) {
            s.cursor().setPos(i + 1, 1);
            s.put(new Character((char)('A' + i), TermAttr.DEFAULT));
        }
        // Reset cursor to row 2 before inserting
        s.cursor().setPos(2, 1);
        s.insertLines(1);
        // Row 2 should now be empty (was cleared after insert)
        assertThat(s.at(2, 1).codepoint()).isEqualTo(' ');
        // Row 1 should still have A
        assertThat(s.at(1, 1).codepoint()).isEqualTo('A');
    }

    @Test
    void deleteLines() {
        Screen s = new Screen(3, 5, 0);
        s.setScrollRegion(1, 3);
        for (int i = 0; i < 3; i++) {
            s.cursor().setPos(i + 1, 1);
            s.put(new Character((char)('A' + i), TermAttr.DEFAULT));
        }
        s.cursor().setPos(2, 1);
        s.deleteLines(1);
        // Row 3 should now be empty
        assertThat(s.at(3, 1).codepoint()).isEqualTo(' ');
    }

    @Test
    void clear() {
        Screen s = createScreen();
        s.put(new Character('X', TermAttr.DEFAULT));
        s.clear();
        assertThat(s.at(1, 1).codepoint()).isEqualTo(' ');
    }

    @Test
    void renderAll() {
        Screen s = new Screen(3, 5, 0);
        s.put(new Character('H', TermAttr.DEFAULT));
        var lines = s.renderAll();
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).startsWith("H");
    }

    @Test
    void invalidConstructorThrows() {
        assertThatThrownBy(() -> new Screen(0, 80, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Screen(24, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Screen(24, 80, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
