package ssg.legoflow.network.telnet.gateway.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.gateway.TelnetGateway;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Demonstrates the Telnet-to-terminal gateway.
 *
 * <p>Shows bridging a Telnet connection to a terminal,
 * automatic option negotiation, and data echo.
 *
 * @since 0.2.0
 */
public final class GatewayDemo {

    private GatewayDemo() {}

    public static void demonstrate() {
        List<byte[]> sentData = new ArrayList<>();

        var terminal = createMockTerminal();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        // Simulate peer sending data
        gateway.feed("Hello from Telnet\r\n".getBytes());

        // Send response to peer
        gateway.send("Welcome!\r\n");

        System.out.println("Terminal type: " + terminal.type());
        System.out.println("Echo enabled: " + gateway.isEchoEnabled());
        System.out.println("Sent packets: " + sentData.size());
    }

    // Simple mock terminal for demonstration — no VT100 dependency required
    static Terminal createMockTerminal() {
        TerminalConfig config = TerminalConfig.builder()
                .rows(24).cols(80).build();
        return new Terminal() {
            @Override public void feed(byte[] data) {}
            @Override public void feed(String text) {}
            @Override public List<String> render() { return Collections.emptyList(); }
            @Override public Cursor cursor() { return new Cursor(1, 1); }
            @Override public TermAttr currentAttr() { return TermAttr.DEFAULT; }
            @Override public TerminalConfig config() { return config; }
            @Override public DisplayModel displayModel() { return null; }
            @Override public void addEventListener(TerminalEventListener listener) {}
            @Override public void removeEventListener(TerminalEventListener listener) {}
            @Override public void reset() {}
            @Override public String type() { return "demo"; }
            @Override public String title() { return "Demo"; }
            @Override public boolean supportsColor() { return false; }
        };
    }
}
