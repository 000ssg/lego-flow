package ssg.legoflow.network.terminals.base.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.io.KeyTranslator;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import java.util.List;
/**
 * Demonstrates core terminal framework features.
 *
 * <p>Shows configuration, factory-based creation, data feeding,
 * cursor positioning, rendering, and key translation.
 *
 * @since 0.2.0
 */
public final class TerminalDemo {

    private TerminalDemo() {}

    /**
     * Demonstrates creating a terminal via the factory, feeding data,
     * and rendering the output.
     */
    public static void demonstrate(String type) {
        TerminalConfig config = TerminalConfig.builder()
                .rows(24)
                .cols(80)
                .colorDepth(256)
                .build();

        Terminal terminal = TerminalFactory.create(type, config);

        // Feed some text
        terminal.feed("Hello, Terminal!\n");
        terminal.feed("Line 2\r\n");

        // Position cursor and write
        terminal.feed("\033[5;10HColored\033[0m");

        // Render the display
        List<String> lines = terminal.render();
        Cursor cursor = terminal.cursor();

        System.out.println("Type: " + terminal.type());
        System.out.println("Cursor: row=" + cursor.row() + ", col=" + cursor.col());
        System.out.println("Color support: " + terminal.supportsColor());
        System.out.println("Lines: " + lines.size());
    }

    /**
     * Demonstrates key translation from named keys to escape sequences.
     */
    public static void demonstrateKeyTranslation() {
        var translator = new KeyTranslator();

        System.out.println("F1:  [" + new String(translator.translate("f1")) + "]");
        System.out.println("Up:  [" + new String(translator.translate("up")) + "]");
        System.out.println("Tab: [" + (char) 0x09 + "]");
    }

    /**
     * Entry point for running demos.
     */
    public static void main(String[] args) {
        demonstrate("vt100");
        demonstrateKeyTranslation();
    }
}
