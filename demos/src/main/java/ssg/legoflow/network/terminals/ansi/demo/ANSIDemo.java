package ssg.legoflow.network.terminals.ansi.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.ansi.ANSITerminal;

/**
 * Demonstrates ANSI X3.64 standard terminal.
 *
 * <p>Shows that DEC private modes are silently ignored
 * while standard CSI sequences work correctly.
 *
 * @since 0.2.0
 */
public final class ANSIDemo {

    private ANSIDemo() {}

    public static void demonstrate() {
        Terminal terminal = ANSITerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("\033[10;20HANSI Text");
        terminal.feed("\033[?25l");

        System.out.println("Type: " + terminal.type());
        System.out.println("Cursor: " + terminal.cursor().row() + "," + terminal.cursor().col());
    }
    public static void main(String[] args) {
        demonstrate();
    }
}
