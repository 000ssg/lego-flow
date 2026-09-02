package ssg.legoflow.network.terminals.vt100.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import java.util.List;
/**
 * Demonstrates VT100 terminal emulation features.
 *
 * <p>Shows CSI cursor motion, SGR attributes, scroll regions,
 * cursor save/restore, and DEC private modes.
 *
 * @since 0.2.0
 */
public final class VT100Demo {

    private VT100Demo() {}

    public static void demonstrate() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).colorDepth(8).build());

        terminal.feed("\033[10;20HVT100 Demo");
        terminal.feed("\033[1;31mBold Red\033[0m");
        terminal.feed("\033[s");
        terminal.feed("\033[1;1HSaved!\033[u");

        List<String> lines = terminal.render();
        System.out.println("Type: " + terminal.type());
        System.out.println("Color: " + terminal.supportsColor());
        System.out.println("Cursor: " + terminal.cursor());
    }
    public static void main(String[] args) {
        demonstrate();
    }
}
