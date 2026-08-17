package ssg.legoflow.network.terminals.vt500.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal.CharSet;

/**
 * Demonstrates VT500 character set features.
 *
 * <p>Shows G0/G1 character set selection, SO/SI switching,
 * and the range of available DEC character sets.
 *
 * @since 0.2.0
 */
public final class VT500Demo {

    private VT500Demo() {}

    public static void demonstrate() {
        Terminal terminal = VT500Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        VT500Terminal vt = (VT500Terminal) terminal;

        System.out.println("Initial charset: " + vt.activeCharset());
        vt.setG0(CharSet.DEC_SPECIAL);
        vt.setG1(CharSet.GERMAN);
        vt.selectG0();
        System.out.println("After SO: " + vt.activeCharset());
        vt.selectG1();
        System.out.println("After SI: " + vt.activeCharset());
    }
}
