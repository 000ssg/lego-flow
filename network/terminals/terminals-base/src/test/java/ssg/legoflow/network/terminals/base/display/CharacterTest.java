package ssg.legoflow.network.terminals.base.display;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CharacterTest {

    @Test
    void emptyCharacter() {
        assertThat(Character.EMPTY.codepoint()).isEqualTo(' ');
        assertThat(Character.EMPTY.attr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void createCharacter() {
        TermAttr attr = TermAttr.builder().bold(true).build();
        Character ch = new Character('A', attr);
        assertThat(ch.codepoint()).isEqualTo('A');
        assertThat(ch.ch()).isEqualTo('A');
        assertThat(ch.attr()).isEqualTo(attr);
    }

    @Test
    void unicodeCharacter() {
        Character ch = new Character(0x2603, TermAttr.DEFAULT); // ☃
        assertThat(ch.codepoint()).isEqualTo(0x2603);
    }

    @Test
    void toStringForPrintable() {
        Character ch = new Character('A', TermAttr.DEFAULT);
        assertThat(ch.toString()).contains("A");
    }

    @Test
    void toStringForControl() {
        Character ch = new Character(0x07, TermAttr.DEFAULT);
        assertThat(ch.toString()).contains("?");
    }

    @Test
    void nullAttrThrows() {
        assertThatThrownBy(() -> new Character('A', null))
                .isInstanceOf(NullPointerException.class);
    }
}
