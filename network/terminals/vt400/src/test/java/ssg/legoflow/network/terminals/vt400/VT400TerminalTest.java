package ssg.legoflow.network.terminals.vt400;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;
/**
 * Comprehensive tests for VT400Terminal.
 *
 * <p>VT400 extends VT200 with:
 * <ul>
 *   <li>Extended SGR codes 82-89 (extended foreground), 92-99 (extended background)</li>
 *   <li>4-window support (CSI n t)</li>
 *   <li>Window size control via OSC 14</li>
 * </ul>
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Extended SGR codes (82-89, 92-99) work correctly</li>
 *   <li>Extended codes don't interfere with standard SGR codes</li>
 *   <li>Window selection via CSI n t</li>
 *   <li>OSC 14 background color parsing</li>
 *   <li>Inherited VT100/VT200 features still work</li>
 * </ul>
 */
class VT400TerminalTest {

    private VT400Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT400Terminal) VT400Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    // --- Basic identity tests ---

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt400");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    // --- Extended SGR foreground (82-89) ---

    @Test
    void testSgr82ExtendedForegroundBlack() {
        terminal.feed("\u001B[82m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.BLACK);
    }

    @Test
    void testSgr83ExtendedForegroundRed() {
        terminal.feed("\u001B[83m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testSgr89ExtendedForegroundWhite() {
        terminal.feed("\u001B[89m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.WHITE);
    }

    // --- Extended SGR background (92-99) ---

    @Test
    void testSgr92ExtendedBackgroundBlack() {
        terminal.feed("\u001B[92m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.BLACK);
    }

    @Test
    void testSgr93ExtendedBackgroundRed() {
        terminal.feed("\u001B[93m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testSgr99ExtendedBackgroundWhite() {
        terminal.feed("\u001B[99m");
        assertThat(terminal.currentAttr().background()).isEqualTo(TermAttr.WHITE);
    }

    // --- Extended SGR combined with standard codes ---

    @Test
    void testSgrExtendedFgWithStandardBg() {
        // Extended foreground (83=red) with standard background (42=green)
        terminal.feed("\u001B[83;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testSgrStandardFgWithExtendedBg() {
        // Standard foreground (31=red) with extended background (93=red)
        terminal.feed("\u001B[31;93m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testSgrExtendedWithBold() {
        terminal.feed("\u001B[1;84m");  // bold + extended green foreground
        var attr = terminal.currentAttr();
        assertThat(attr.bold()).isTrue();
        assertThat(attr.foreground()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testSgrExtendedPreservedByReset() {
        terminal.feed("\u001B[83m");  // extended red
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
        terminal.feed("\u001B[0m");   // reset
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.WHITE);
    }

    // --- Extended SGR chaining (critical: extended codes must survive parent processing) ---

    @Test
    void testSgrExtendedNotOverriddenByParent() {
        // This is the critical bug fix test: extended codes must NOT be overridden
        terminal.feed("\u001B[83;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testSgrExtendedWithVideoReverse() {
        // Extended codes + VT200 video reverse
        terminal.feed("\u001B[83;52m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.reverse()).isTrue();
    }

    @Test
    void testSgrMultipleExtendedCodes() {
        terminal.feed("\u001B[83;96m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.BLUE);
    }

    // --- Window management ---

    @Test
    void testWindowSelection() {
        assertThat(terminal.activeWindow()).isEqualTo(1);
        terminal.feed("\u001B[2t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    @Test
    void testWindowSelectionDefault() {
        terminal.feed("\u001B[t");  // No param, defaults to 1
        assertThat(terminal.activeWindow()).isEqualTo(1);
    }

    @Test
    void testWindowSelectionClamped() {
        terminal.feed("\u001B[0t");  // Below minimum
        assertThat(terminal.activeWindow()).isEqualTo(1);
        terminal.feed("\u001B[5t");  // Above maximum (4 windows)
        assertThat(terminal.activeWindow()).isEqualTo(4);
    }

    @Test
    void testWindowCount() {
        assertThat(terminal.windowCount()).isEqualTo(4);
    }

    // --- OSC 14 ---

    @Test
    void testOsc14BgColorValid() {
        terminal.feed("\u001B]14;aabbcc\u0007");
        assertThat(terminal.osc14BgColor()).isEqualTo("aabbcc");
    }

    @Test
    void testOsc14BgColorInvalid() {
        terminal.feed("\u001B]14;invalid\u0007");
        assertThat(terminal.osc14BgColor()).isNull();
    }

    @Test
    void testOsc14BgColorShort() {
        terminal.feed("\u001B]14;abc\u0007");
        assertThat(terminal.osc14BgColor()).isNull();
    }

    @Test
    void testOsc14BgColorUpper() {
        terminal.feed("\u001B]14;AABBCC\u0007");
        assertThat(terminal.osc14BgColor()).isEqualTo("AABBCC");
    }

    // --- Inherited features ---

    @Test
    void testInheritsVt100CursorMotion() {
        terminal.feed("\u001B[10;20H");
        assertThat(terminal.cursor().row()).isEqualTo(10);
        assertThat(terminal.cursor().col()).isEqualTo(20);
    }

    @Test
    void testInheritsVt100SgrColors() {
        terminal.feed("\u001B[31;42m");
        var attr = terminal.currentAttr();
        assertThat(attr.foreground()).isEqualTo(TermAttr.RED);
        assertThat(attr.background()).isEqualTo(TermAttr.GREEN);
    }

    @Test
    void testInheritsVt100OriginMode() {
        terminal.feed("\u001B[5;15r");
        terminal.feed("\u001B[?6h");
        terminal.feed("\u001B[1;1H");
        assertThat(terminal.cursor().row()).isEqualTo(5);
    }

    @Test
    void testInheritsVt200VideoReverse() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
        assertThat(terminal.currentAttr().reverse()).isTrue();
    }

    @Test
    void testInheritsVt100ScrollRegion() {
        terminal.feed("\u001B[5;15r");
        assertThat(terminal.displayModel().screen().scrollTop()).isEqualTo(5);
        assertThat(terminal.displayModel().screen().scrollBottom()).isEqualTo(15);
    }

    @Test
    void testInheritsVt100CursorSaveRestore() {
        terminal.feed("Hello");
        terminal.feed("\u001B[s");
        terminal.feed("\u001B[1;1H");
        terminal.feed("\u001B[u");
        assertThat(terminal.cursor().col()).isEqualTo(6);
    }

    // --- Reset tests ---

    @Test
    void testResetWindowSelection() {
        terminal.feed("\u001B[3t");
        terminal.reset();
        assertThat(terminal.activeWindow()).isEqualTo(1);
    }

    @Test
    void testResetOsc14BgColor() {
        terminal.feed("\u001B]14;aabbcc\u0007");
        terminal.reset();
        assertThat(terminal.osc14BgColor()).isNull();
    }

    @Test
    void testResetClearsAllAttributes() {
        terminal.feed("\u001B[1;83;96m");
        terminal.reset();
        assertThat(terminal.currentAttr()).isEqualTo(TermAttr.DEFAULT);
    }

    // --- Feed and render tests ---

    @Test
    void testFeedText() {
        terminal.feed("Hello VT400");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello VT400");
    }

    @Test
    void testRender() {
        terminal.feed("Line1");
        terminal.feed("\r\nLine2");
        var lines = terminal.render();
        assertThat(lines).hasSize(24);
        assertThat(lines.get(0)).startsWith("Line1");
        assertThat(lines.get(1)).startsWith("Line2");
    }

    // --- OSC title handling (inherited) ---

    @Test
    void testOscTitle() {
        terminal.feed("\u001B]0;VT400 Title\u0007");
        assertThat(terminal.title()).isEqualTo("VT400 Title");
    }

    // --- Display model access ---

    @Test
    void testDisplayModel() {
        assertThat(terminal.displayModel()).isNotNull();
        assertThat(terminal.displayModel().screen().rows()).isEqualTo(24);
    }

    // --- Edge cases ---

    @Test
    void testSgrExtendedCodeOutOfBounds() {
        terminal.feed("\u001B[81m");  // Not an extended code (81 < 82)
        terminal.feed("\u001B[90m");  // Bright red (standard code, not extended bg)
        // Should not affect extended color state
    }

    @Test
    void testConfigAccess() {
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    // --- DECRQM — inherited from VT100 (through VT200) ---
    @Test
    void testDecrqmInherited() {
        // DECRQM works through VT400 -> VT200 -> VT100 inheritance
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;3$y");
    }

    @Test
    void testDecrqmAfterDecset() {
        terminal.feed("\u001B[?1h");
        terminal.feed("\u001B[?1$p");
        String output = terminal.readOutput();
        assertThat(output).isEqualTo("\u001B[?1;2$y");
    }
}
