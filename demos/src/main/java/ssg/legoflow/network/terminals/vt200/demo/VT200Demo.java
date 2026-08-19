package ssg.legoflow.network.terminals.vt200.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt200.VT200Terminal;

/**
 * Demonstrates VT200 terminal extensions.
 *
 * <p>Shows SGR 52 (video reverse) and SGR 55 (video normal)
 * in addition to all VT100 features.
 *
 * @since 0.2.0
 */
public final class VT200Demo {

    private VT200Demo() {}

    public static void demonstrate() {
        Terminal terminal = VT200Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("Normal text\n");
        terminal.feed("\033[52mReversed\n");
        terminal.feed("\033[55mNormal\n");

        System.out.println("Type: " + terminal.type());
        System.out.println("Color: " + terminal.supportsColor());
    }
}
