package ssg.legoflow.network.terminals.base.io;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class KeyTranslatorTest {

    @Test
    void arrowKeys() {
        KeyTranslator kt = new KeyTranslator();
        assertThat(kt.translate("up")).isEqualTo("\u001B[A".getBytes());
        assertThat(kt.translate("down")).isEqualTo("\u001B[B".getBytes());
        assertThat(kt.translate("right")).isEqualTo("\u001B[C".getBytes());
        assertThat(kt.translate("left")).isEqualTo("\u001B[D".getBytes());
    }

    @Test
    void functionKeys() {
        KeyTranslator kt = new KeyTranslator();
        assertThat(kt.translate("f1")).isEqualTo("\u001BOP".getBytes());
        assertThat(kt.translate("f4")).isEqualTo("\u001BOS".getBytes());
    }

    @Test
    void navigationKeys() {
        KeyTranslator kt = new KeyTranslator();
        assertThat(kt.translate("home")).isEqualTo("\u001B[H".getBytes());
        assertThat(kt.translate("insert")).isEqualTo("\u001B[2~".getBytes());
        assertThat(kt.translate("delete")).isEqualTo("\u001B[3~".getBytes());
    }

    @Test
    void controlCharacters() {
        assertThat(KeyTranslator.translateControl('A')).isEqualTo((byte) 1);
        assertThat(KeyTranslator.translateControl('a')).isEqualTo((byte) 1);
        assertThat(KeyTranslator.translateControl('Z')).isEqualTo((byte) 26);
    }

    @Test
    void caseInsensitive() {
        KeyTranslator kt = new KeyTranslator();
        assertThat(kt.translate("UP")).isNotNull();
        assertThat(kt.translate("Up")).isNotNull();
    }

    @Test
    void unknownKeyReturnsNull() {
        KeyTranslator kt = new KeyTranslator();
        assertThat(kt.translate("someUnknownKey")).isNull();
    }
}
