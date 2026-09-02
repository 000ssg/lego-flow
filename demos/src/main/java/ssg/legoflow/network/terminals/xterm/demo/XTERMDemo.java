package ssg.legoflow.network.terminals.xterm.demo;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.xterm.XTERMTerminal;
/**
 * Demonstrates XTERM terminal features.
 *
 * <p>Shows 256-color, true color RGB, mouse tracking modes,
 * bracketed paste, synchronized output, and underline styles.
 *
 * @since 0.2.0
 */
public final class XTERMDemo {

    private XTERMDemo() {}

    public static void demonstrate() {
        Terminal terminal = XTERMTerminal.create(TerminalConfig.builder()
                .rows(24).cols(80).colorDepth(256).build());

        XTERMTerminal xterm = (XTERMTerminal) terminal;

        terminal.feed("\033[38;5;196mRed\033[0m");
        terminal.feed("\033[38;2;100;150;200mRGB\033[0m");
        terminal.feed("\033[?1000h");
        terminal.feed("\033[?2024h");

        System.out.println("Mouse mode: " + xterm.mouseMode());
        System.out.println("Bracketed paste: " + xterm.isBracketedPaste());
        System.out.println("Type: " + terminal.type());
    }
    public static void main(String[] args) {
        demonstrate();
    }
}
