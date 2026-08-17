package ssg.legoflow.network.terminals.vt400.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt400.VT400Terminal;

/**
 * Demonstrates VT400 workstation features.
 *
 * <p>Shows window selection (CSI n t) and extended SGR
 * color codes (82-89, 92-99).
 *
 * @since 0.2.0
 */
public final class VT400Demo {

    private VT400Demo() {}

    public static void demonstrate() {
        Terminal terminal = VT400Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[1t");
        terminal.feed("\033[2t");

        System.out.println("Type: " + terminal.type());
    }
}
