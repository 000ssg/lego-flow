package ssg.legoflow.network.terminals.tn3270;

import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.assertThat;
/**
 * Tests for TN3270 keyboard map.
 */
class TN3270KeyboardMapTest {

    private final TN3270KeyboardMap map = TN3270KeyboardMap.getInstance();

    @Test
    void testSingleton() {
        assertThat(TN3270KeyboardMap.getInstance()).isSameAs(map);
    }

    @Test
    void testPrintableKeys() {
        assertThat(map.keyCode("A")).isEqualTo((byte) 'A');
        assertThat(map.keyCode("Z")).isEqualTo((byte) 'Z');
        assertThat(map.keyCode("a")).isEqualTo((byte) 'a');
        assertThat(map.keyCode("0")).isEqualTo((byte) '0');
        assertThat(map.keyCode("9")).isEqualTo((byte) '9');
        assertThat(map.keyCode(" ")).isEqualTo((byte) ' ');
        assertThat(map.keyCode("-")).isEqualTo((byte) '-');
        assertThat(map.keyCode("=")).isEqualTo((byte) '=');
    }

    @Test
    void testPFKeys() {
        assertThat(map.keyCode("PF1")).isEqualTo(TN3270KeyboardMap.PF1);
        assertThat(map.keyCode("PF12")).isEqualTo(TN3270KeyboardMap.PF12);
        assertThat(map.keyCode("PF10")).isEqualTo(TN3270KeyboardMap.PF10);
    }

    @Test
    void testApplicationKeys() {
        assertThat(map.keyCode("PA1")).isEqualTo(TN3270KeyboardMap.PA1);
        assertThat(map.keyCode("PA2")).isEqualTo(TN3270KeyboardMap.PA2);
        assertThat(map.keyCode("PA3")).isEqualTo(TN3270KeyboardMap.PA3);
        assertThat(map.keyCode("ATN")).isEqualTo(TN3270KeyboardMap.ATTN);
    }

    @Test
    void testCursorKeys() {
        assertThat(map.keyCode("CURSOR_UP")).isEqualTo(TN3270KeyboardMap.CURSOR_UP);
        assertThat(map.keyCode("CURSOR_DOWN")).isEqualTo(TN3270KeyboardMap.CURSOR_DOWN);
        assertThat(map.keyCode("CURSOR_RIGHT")).isEqualTo(TN3270KeyboardMap.CURSOR_RIGHT);
        assertThat(map.keyCode("CURSOR_LEFT")).isEqualTo(TN3270KeyboardMap.CURSOR_LEFT);
    }

    @Test
    void testNavigationKeys() {
        assertThat(map.keyCode("HOME")).isEqualTo(TN3270KeyboardMap.HOME);
        assertThat(map.keyCode("PAGE_UP")).isEqualTo(TN3270KeyboardMap.PAGE_UP);
        assertThat(map.keyCode("PAGE_DOWN")).isEqualTo(TN3270KeyboardMap.PAGE_DOWN);
        assertThat(map.keyCode("BACK_TAB")).isEqualTo(TN3270KeyboardMap.BACK_TAB);
    }

    @Test
    void testEditKeys() {
        assertThat(map.keyCode("INSERT")).isEqualTo(TN3270KeyboardMap.INSERT);
        assertThat(map.keyCode("ERASE_INPUT")).isEqualTo(TN3270KeyboardMap.ERASE_INPUT);
        assertThat(map.keyCode("ERASE_FIELD")).isEqualTo(TN3270KeyboardMap.ERASE_FIELD);
        assertThat(map.keyCode("ERASE_ALL")).isEqualTo(TN3270KeyboardMap.ERASE_ALL);
        assertThat(map.keyCode("HELP")).isEqualTo(TN3270KeyboardMap.HELP);
    }

    @Test
    void testKeyName() {
        assertThat(map.keyName(TN3270KeyboardMap.PF1)).isEqualTo("PF1");
        assertThat(map.keyName((byte) 'A')).isEqualTo("A");
        assertThat(map.keyName(TN3270KeyboardMap.CURSOR_UP)).isEqualTo("CURSOR_UP");
    }

    @Test
    void testUnknownKeyName() {
        assertThat(map.keyName((byte) 0x00)).isNull();
    }

    @Test
    void testUnknownKeyCode() {
        assertThat(map.keyCode("UNKNOWN")).isEqualTo((byte) 0);
    }

    @Test
    void testFunctionKey() {
        assertThat(TN3270KeyboardMap.isFunctionKey(TN3270KeyboardMap.PF1)).isTrue();
        assertThat(TN3270KeyboardMap.isFunctionKey(TN3270KeyboardMap.PF12)).isTrue();
        assertThat(TN3270KeyboardMap.isFunctionKey(TN3270KeyboardMap.PA1)).isTrue();
        assertThat(TN3270KeyboardMap.isFunctionKey((byte) 'A')).isFalse();
    }

    @Test
    void testControlKey() {
        assertThat(TN3270KeyboardMap.isControlKey(TN3270KeyboardMap.HOME)).isTrue();
        assertThat(TN3270KeyboardMap.isControlKey(TN3270KeyboardMap.HELP)).isTrue();
        assertThat(TN3270KeyboardMap.isControlKey((byte) 'A')).isFalse();
    }

    @Test
    void testPrintableKey() {
        assertThat(TN3270KeyboardMap.isPrintableKey((byte) 'A')).isTrue();
        assertThat(TN3270KeyboardMap.isPrintableKey((byte) ' ')).isTrue();
        assertThat(TN3270KeyboardMap.isPrintableKey((byte) '~')).isTrue();
        assertThat(TN3270KeyboardMap.isPrintableKey(TN3270KeyboardMap.PF1)).isFalse();
    }

    @Test
    void testKeyboardSize() {
        assertThat(TN3270KeyboardMap.KEYBOARD_SIZE).isEqualTo(32);
    }

    @Test
    void testSpecialKeys() {
        assertThat(TN3270KeyboardMap.LOCK).isEqualTo((byte) 0x1F);
        assertThat(TN3270KeyboardMap.SCROLL_UP).isEqualTo((byte) 0x19);
        assertThat(TN3270KeyboardMap.SCROLL_DOWN).isEqualTo((byte) 0x1A);
    }
}
