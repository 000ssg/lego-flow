package ssg.legoflow.network.terminals.vt52.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt52.VT52Terminal;
import java.util.List;
/**
 * Demonstrates VT52 terminal emulation.
 *
 * <p>Shows VT52-specific cursor addressing (ESC Y),
 * cursor motion commands, and display clearing.
 *
 * @since 0.2.0
 */
public final class VT52Demo {

    private VT52Demo() {}

    public static void demonstrate() {
        Terminal terminal = VT52Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());

        terminal.feed("VT52 Terminal Demo\r\n");
        terminal.feed("Hello from VT52!\r\n");

        // VT52 cursor address: ESC Y row+32 col+32
        terminal.feed("\033Y@A");

        List<String> lines = terminal.render();
        System.out.println("Type: " + terminal.type());
        System.out.println("Cursor: " + terminal.cursor().row() + "," + terminal.cursor().col());
        System.out.println("Lines: " + lines.size());
    }
    public static void main(String[] args) {
        demonstrate();
    }
}
