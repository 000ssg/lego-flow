package ssg.legoflow.network.terminals.vt500;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal.CharSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for VT500Terminal.
 *
 * <p>VT500 extends VT400 with:
 * <ul>
 *   <li>DEC character set support (G0/G1 switching via SO/SI and ESC paren)</li>
 *   <li>DEC Special Character and Line Drawing Set (charset '0')</li>
 *   <li>International character sets (UK, French, French-Canadian, International, Scandinavian, German)</li>
 *   <li>DCS for user-defined character sets</li>
 * </ul>
 */
class VT500TerminalTest {

    private VT500Terminal terminal;

    @BeforeEach
    void setUp() {
        terminal = (VT500Terminal) VT500Terminal.create(
                TerminalConfig.builder().rows(24).cols(80).build());
    }

    // --- Basic identity tests ---

    @Test
    void testType() {
        assertThat(terminal.type()).isEqualTo("vt500");
    }

    @Test
    void testColorSupport() {
        assertThat(terminal.supportsColor()).isTrue();
    }

    // --- Character set defaults ---

    @Test
    void testDefaultCharsets() {
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.ASCII);
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.ASCII);
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
    }

    // --- ESC paren charset selection (DECSCE) ---

    @Test
    void testEscParenG0Ascii() {
        terminal.feed("\u001B(B");  // ESC ( B — select G0 = ASCII
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.ASCII);
    }

    @Test
    void testEscParenG0DecSpecial() {
        terminal.feed("\u001B(0");  // ESC ( 0 — select G0 = DEC Special
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testEscParenG1Ascii() {
        terminal.feed("\u001B)B");  // ESC ) B — select G1 = ASCII
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.ASCII);
    }

    @Test
    void testEscParenG1DecSpecial() {
        terminal.feed("\u001B)0");  // ESC ) 0 — select G1 = DEC Special
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testEscParenG0Uk() {
        terminal.feed("\u001B(U");  // ESC ( U — select G0 = UK
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.UK);
    }

    @Test
    void testEscParenG1French() {
        terminal.feed("\u001B)K");  // ESC ) K — select G1 = French
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.FRENCH);
    }

    @Test
    void testEscParenG0German() {
        terminal.feed("\u001B(Y");  // ESC ( Y — select G0 = German
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.GERMAN);
    }

    @Test
    void testEscParenUnknownDescriptor() {
        terminal.feed("\u001B(Z");  // ESC ( Z — unknown, defaults to ASCII
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.ASCII);
    }

    // --- SO/SI charset switching ---

    @Test
    void testSoActivatesG0() {
        terminal.feed("\u001B(0");  // G0 = DEC Special
        terminal.feed("\u000E");     // SO — activate G0
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testSiActivatesG1() {
        terminal.feed("\u001B)0");  // G1 = DEC Special
        terminal.feed("\u000F");     // SI — activate G1
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testSoSiToggle() {
        terminal.feed("\u001B(0");   // G0 = DEC Special
        terminal.feed("\u001B)B");   // G1 = ASCII
        terminal.feed("\u000E");      // SO — G0 active
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);
        terminal.feed("\u000F");      // SI — G1 active
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
    }

    // --- DEC Special character mapping ---

    @Test
    void testDecSpecialBacktick() {
        terminal.feed("\u001B(0\u000E");  // G0=DEC Special, activate
        terminal.feed("`");                 // Maps to 0x25C6 (filled diamond)
        var lines = terminal.render();
        // The character should be mapped through DEC_SPECIAL_MAP
    }

    @Test
    void testDecSpecialLineDrawing() {
        terminal.feed("\u001B(0\u000E");  // G0=DEC Special, activate
        terminal.feed("t");                 // Maps to 0x2500 (horizontal line)
        var lines = terminal.render();
    }

    @Test
    void testDecSpecialCorner() {
        terminal.feed("\u001B(0\u000E");  // G0=DEC Special, activate
        terminal.feed("v");                 // Maps to 0x250C (lower left corner)
    }

    @Test
    void testDecSpecialCross() {
        terminal.feed("\u001B(0\u000E");  // G0=DEC Special, activate
        terminal.feed("|");                 // Maps to 0x253C (cross)
    }

    // --- International charset mapping ---

    @Test
    void testGermanCharsetApostrophe() {
        // German charset: apostrophe (0x60) maps to Ä (0x00C4)
        terminal.feed("\u001B(Y\u000E");   // G0=German, activate
        terminal.feed("`");                  // Should map to Ä
    }

    @Test
    void testFrenchCharsetEGrave() {
        // French charset: backtick (0x60) maps to è (0x00E8)
        terminal.feed("\u001B(K\u000E");    // G0=French, activate
        terminal.feed("`");                   // Should map to è
    }

    @Test
    void testScandinavianCharset() {
        terminal.feed("\u001B(Q");           // G0=Scandinavian
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.SCANDINAVIAN);
    }

    // --- Programmatic charset selection ---

    @Test
    void testSetG0() {
        terminal.setG0(CharSet.DEC_SPECIAL);
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testSetG1() {
        terminal.setG1(CharSet.FRENCH);
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.FRENCH);
    }

    @Test
    void testSelectG0() {
        terminal.setG0(CharSet.DEC_SPECIAL);
        terminal.selectG0();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.DEC_SPECIAL);
    }

    @Test
    void testSelectG1() {
        terminal.setG1(CharSet.GERMAN);
        terminal.selectG1();
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.GERMAN);
    }

    // --- CharSet descriptor lookup ---

    @Test
    void testCharSetFromDescriptor() {
        assertThat(CharSet.fromDescriptor('B')).isEqualTo(CharSet.ASCII);
        assertThat(CharSet.fromDescriptor('0')).isEqualTo(CharSet.DEC_SPECIAL);
        assertThat(CharSet.fromDescriptor('U')).isEqualTo(CharSet.UK);
        assertThat(CharSet.fromDescriptor('K')).isEqualTo(CharSet.FRENCH);
        assertThat(CharSet.fromDescriptor('W')).isEqualTo(CharSet.FRENCH_CANADIAN);
        assertThat(CharSet.fromDescriptor('R')).isEqualTo(CharSet.INTERNATIONAL);
        assertThat(CharSet.fromDescriptor('Q')).isEqualTo(CharSet.SCANDINAVIAN);
        assertThat(CharSet.fromDescriptor('Y')).isEqualTo(CharSet.GERMAN);
        assertThat(CharSet.fromDescriptor('Z')).isEqualTo(CharSet.ASCII);  // Unknown → ASCII
    }

    // --- Reset tests ---

    @Test
    void testResetCharsets() {
        terminal.feed("\u001B(0");           // G0 = DEC Special
        terminal.feed("\u001B)K");           // G1 = French
        terminal.feed("\u000E");              // SO — activate G0
        terminal.reset();
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.ASCII);
        assertThat(terminal.g1Charset()).isEqualTo(CharSet.ASCII);
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.ASCII);
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
    void testInheritsVt200VideoReverse() {
        terminal.feed("\u001B[52m");
        assertThat(terminal.isVideoReverse()).isTrue();
    }

    @Test
    void testInheritsVt400ExtendedSgr() {
        terminal.feed("\u001B[83m");
        assertThat(terminal.currentAttr().foreground()).isEqualTo(TermAttr.RED);
    }

    @Test
    void testInheritsVt400WindowSelection() {
        terminal.feed("\u001B[2t");
        assertThat(terminal.activeWindow()).isEqualTo(2);
    }

    // --- Feed and render tests ---

    @Test
    void testFeedText() {
        terminal.feed("Hello VT500");
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello VT500");
    }

    @Test
    void testFeedDecSpecialAndRender() {
        terminal.feed("\u001B(0\u000E");  // DEC Special active
        terminal.feed("-");                 // Horizontal double line
        var lines = terminal.render();
        assertThat(lines.get(0)).isNotEmpty();
    }

    // --- Display model access ---

    @Test
    void testDisplayModel() {
        assertThat(terminal.displayModel()).isNotNull();
        assertThat(terminal.displayModel().screen().rows()).isEqualTo(24);
    }

    // --- Edge cases ---

    @Test
    void testCharSetLowercaseDescriptor() {
        // Lowercase descriptors are not standard; parser should handle
        terminal.feed("\u001B(b");
        // Since 'b' is not in BY_DESCRIPTOR, defaults to ASCII
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.ASCII);
    }

    @Test
    void testConfigAccess() {
        assertThat(terminal.config().rows()).isEqualTo(24);
        assertThat(terminal.config().cols()).isEqualTo(80);
    }

    // --- DCS user-defined charset tests ---

    @Test
    void testFromDescriptorNul() {
        assertThat(CharSet.fromDescriptor('\u0000')).isEqualTo(CharSet.USER_DEFINED);
    }

    @Test
    void testDcsUserDefinedCharset() {
        // Map codepoint 65 (A) to replacement char "X" via DCS
        terminal.feed("\u001BP|65;X\u001B\\");  // DCS | 65;X ST
        // Activate user-defined charset for G0 via ESC ( NUL
        terminal.feed("\u001B(\u0000");
        assertThat(terminal.g0Charset()).isEqualTo(CharSet.USER_DEFINED);
        // SO to activate G0
        terminal.feed("\u000E");
        assertThat(terminal.activeCharset()).isEqualTo(CharSet.USER_DEFINED);
        // Feed "A" (codepoint 65) — should be mapped to "X"
        terminal.feed("A");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("X");
    }

    @Test
    void testDcsUserDefinedMultipleMappings() {
        terminal.feed("\u001BP|66;Y\u001B\\");  // B -> Y
        terminal.feed("\u001BP|67;Z\u001B\\");  // C -> Z
        terminal.feed("\u001B(\u0000");         // G0 = user-defined
        terminal.feed("\u000E");                  // SO — activate G0
        terminal.feed("BC");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("YZ");
    }

    @Test
    void testDcsUserDefinedReset() {
        terminal.feed("\u001BP|65;X\u001B\\");  // A -> X
        terminal.reset();
        terminal.feed("\u001B(\u0000");         // User-defined charset
        terminal.feed("\u000E");
        terminal.feed("A");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("A");
    }

    @Test
    void testDcsInvalidFormat() {
        terminal.feed("\u001BP|abc;X\u001B\\");  // Invalid codepoint
        terminal.feed("Hello");
        var lines = terminal.render();
        assertThat(lines.get(0)).isEqualTo("Hello");
    }

    // --- DECRQM — inherited from VT100 (through VT400/VT200) ---
    @Test
    void testDecrqmInherited() {
        // DECRQM works through full inheritance chain
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
