package ssg.legoflow.network.terminals.vt200;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;
/**
 * Comprehensive tests for VT200Terminal.
 *
 * <p>VT200 extends VT100 with:
 * <ul>
 *   <li>SGR 52 (video reverse on) and SGR 55 (video normal)</li>
 *   <li>Function key support (PF1–PF3, PL1–PL6)</li>
 * </ul>
 *
 * <p>These tests verify:
 * <ul>
 *   <li>VT200-specific SGR codes (52, 55)</li>
 *   <li>SGR code chaining — VT200 codes work correctly with VT100 codes</li>
 *   <li>Video reverse properly updates TermAttr.reverse</li>
 *   <li>Inherited VT100 features still work</li>
 * </ul>
 */
class VT200TerminalTest {

    private VT200Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT200Terminal) VT200Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    // --- Basic identity tests ---

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt200");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    // --- VT200 SGR 52 (video reverse) tests ---

    @Test
    void testSgr52VideoReverse() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    @Test
    void testSgr55VideoNormal() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(terminal.isVideoReverse()).isFalse();
        assertThat(terminal.currentAttr().reverse()).isFalse();
    }

    @Test
    void testSgr52ToggleReverse() {
        // Video reverse should toggle the reverse attribute
        terminal.feed("\u001B[52m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(terminal.currentAttr().reverse()).isFalse();
        terminal.feed("\u001B[52m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    // --- VT200 SGR chaining with VT100 codes ---

    @Test
    void testSgr52CombinedWithBold() {
        // SGR 52 and SGR 1 (bold) in same sequence
        terminal.feed("\u001B[52;1m");
        assertThat(terminal.isVideoReverse()).isTrue();
        assertThat(terminal.currentAttr().reverse()).isTrue();
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void testSgr52CombinedWithForegroundColor() {
        // SGR 52 and SGR 31 (red) in same sequence
        terminal.feed("\u001B[52;31m");
        assertThat(terminal.isVideoReverse()).isTrue();
        assertThat(terminal.currentAttr().reverse()).isTrue();
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testSgr55CombinedWithReset() {
        // SGR 55 and SGR 0 (reset) in same sequence
        terminal.feed("\u001B[1m");
        terminal.feed("\u001B[55;0m");
        assertThat(terminal.isVideoReverse()).isFalse();
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    @Test
    void testSgr52PreservesOtherAttributes() {
        // Setting video reverse should not affect other attributes
        terminal.feed("\u001B[1;3;31m");  // bold, italic, red
        terminal.feed("\u001B[52m");       // video reverse
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.italic()).isTrue();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.reverse()).isTrue();
    }

    @Test
    void testSgr55PreservesOtherAttributes() {
        // Clearing video reverse should not affect other attributes
        terminal.feed("\u001B[1;31;52m");  // bold, red, reverse
        terminal.feed("\u001B[55m");         // video normal
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.reverse()).isFalse();
    }

    // --- VT200 SGR with VT100 SGR 7 (reverse) interaction ---

    @Test
    void testSgr7VsSgr52() {
        // Both SGR 7 and SGR 52 should set reverse attribute
        terminal.feed("\u001B[7m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
        terminal.feed("\u001B[52m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
        assertThat(terminal.isVideoReverse()).isTrue();
    }

    @Test
    void testSgr27ClearsReverseFromSgr52() {
        // SGR 27 should clear reverse set by SGR 52
        terminal.feed("\u001B[52m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
        terminal.feed("\u001B[27m");
        assertThat(terminal.currentAttr().reverse()).isFalse();
    }

    @Test
    void testSgr52AfterSgr7() {
        terminal.feed("\u001B[7m");
        terminal.feed("\u001B[27m");
        assertThat(terminal.currentAttr().reverse()).isFalse();
        terminal.feed("\u001B[52m");
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    // --- Inherited VT100 features ---

    @Test
    void testInheritsVt100CursorMotion() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testInheritsVt100SgrBold() {
        terminal.feed("\u001B[1m");
        assertThat(terminal.currentAttr().bold()).isTrue();
    }

    @Test
    void testInheritsVt100SgrUnderline() {
        terminal.feed("\u001B[4m");
        assertThat(terminal.currentAttr().underline()).isEqualTo(TermAttr.UNDERLINE_SINGLE);
    }

    @Test
    void testInheritsVt100SgrColors() {
        terminal.feed("\u001B[31;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testInheritsVt100CursorSaveRestore() {
        terminal.feed("Hello");
        terminal.feed("\u001B[s");
        terminal.feed("\u001B[1;1H");
        terminal.feed("\u001B[u");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    @Test
    void testInheritsVt100OriginMode() {
        terminal.feed("\u001B[5;15r");
        terminal.feed("\u001B[?6h");
        terminal.feed("\u001B[1;1H");
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void testInheritsVt100ScrollRegion() {
        terminal.feed("\u001B[5;15r");
        assertThat(terminal.displayModel().screen().scrollTop()).isEqualTo(5);
        assertThat(terminal.displayModel().screen().scrollBottom()).isEqualTo(15);
    }

    // --- Reset tests ---

    @Test
    void testResetClearsVideoReverse() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        terminal.reset();
        assertThat(terminal.isVideoReverse()).isFalse();
        assertThat(terminal.currentAttr().reverse()).isFalse();
    }

    @Test
    void testResetClearsAllAttributes() {
        terminal.feed("\u001B[1;31;52m");
        terminal.reset();
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
        assertThat(terminal.isVideoReverse()).isFalse();
    }

    // --- Feed text tests ---

    @Test
    void testFeedText() {
        terminal.feed("Hello VT200");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello VT200");
    }

    @Test
    void testFeedByteArray() {
        terminal.feed("Test".getBytes());
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Test");
    }

    // --- Display model access ---

    @Test
    void testDisplayModel() {
        assertThat(terminal.displayModel()).isNotNull();
        assertThat(terminal.displayModel().screen().rows()).isEqualTo(24);
        assertThat(terminal.displayModel().screen().cols()).isEqualTo(80);
    }

    // --- Render tests ---

    @Test
    void testRender() {
        terminal.feed("Line1");
        terminal.feed("\r\nLine2");
        var lines = terminal.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).startsWith("Line1");
        assertThat(lines.get(1)).startsWith("Line2");
    }

    // --- Edge cases ---

    @Test
    void testSgr52MultipleTimes() {
        terminal.feed("\u001B[52m\u001B[52m\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
    }

    @Test
    void testSgr5255Alternating() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(terminal.isVideoReverse()).isFalse();
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        terminal.feed("\u001B[55m");
        assertThat(terminal.isVideoReverse()).isFalse();
    }

    @Test
    void testConfigAccess() {
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    // --- DECRQM — inherited from VT100 ---
    @Test
    void testDecrqmInherited() {
        // DECRQM works through VT200 -> VT100 inheritance
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;3$y");
    }

    @Test
    void testDecrqmMode7Inherited() {
        terminal.feed("\u001B[?7$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?7;2$y");
    }
}
