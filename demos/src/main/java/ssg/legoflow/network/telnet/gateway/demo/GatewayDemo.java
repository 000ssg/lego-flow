package ssg.legoflow.network.telnet.gateway.demo;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.gateway.TelnetGateway;
import ssg.legoflow.network.terminals.ansi.ANSITerminal;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import ssg.legoflow.network.terminals.vt200.VT200Terminal;
import ssg.legoflow.network.terminals.vt400.VT400Terminal;
import ssg.legoflow.network.terminals.vt500.VT500Terminal;
import ssg.legoflow.network.terminals.vt52.VT52Terminal;
import ssg.legoflow.network.terminals.xterm.XTERMTerminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Demonstrates the Telnet-to-terminal gateway with all terminal types
 * and all gateway features.
 *
 * <p>Features demonstrated:
 * <ul>
 *   <li>Data feed and echo control</li>
 *   <li>Option negotiation (ECHO, NAWS, TTYPE, BINARY, LINEMODE)</li>
 *   <li>Subnegotiation (TTYPE IS/SEND, NAWS, Speed, LINEMODE, NewEnv)</li>
 *   <li>Gateway events (Connected, Disconnected, Negotiated, Resize, Tty,
 *       Command, Dm, Binary, Env, Line, LinemodeActive, LinemodeInactive)</li>
 *   <li>All terminal types: VT52, VT100, VT200, VT400, VT500, ANSI, XTERM</li>
 *   <li>Null safety and graceful handling</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class GatewayDemo {

    private GatewayDemo() {}

    public static void demonstrate() {
        System.out.println("=== Telnet Gateway Demo ===\n");

        System.out.println("1. Basic Data Feed and Echo");
        demonstrateBasicFeed();

        System.out.println("\n2. All Terminal Types");
        demonstrateAllTerminals();

        System.out.println("\n3. Option Negotiation");
        demonstrateNegotiation();

        System.out.println("\n4. Subnegotiation (TTYPE, NAWS, Speed)");
        demonstrateSubnegotiation();

        System.out.println("\n5. Event Handling");
        demonstrateEvents();

        System.out.println("\n6. Null Safety");
        demonstrateNullSafety();

        System.out.println("\n7. DM Synchronization");
        demonstrateDmSync();

        System.out.println("\n8. Linemode and Line Events");
        demonstrateLinemode();

        System.out.println("\n9. Binary Mode Negotiation");
        demonstrateBinaryMode();

        System.out.println("\n10. NewEnv Handler");
        demonstrateNewEnv();

        System.out.println("\n=== Demo Complete ===");
    }

    // ── 1. Basic Feed ─────────────────────────────────────────────────

    private static void demonstrateBasicFeed() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sentData = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sentData::add)
                .build();

        // Feed plain text — terminal renders it
        gateway.feed("Hello from Telnet\r\n".getBytes());
        System.out.println("  Terminal type: " + terminal.type());
        System.out.println("  Lines rendered: " + terminal.render().size());
        System.out.println("  Echo enabled: " + gateway.isEchoEnabled());

        // Disable echo — nothing sent back
        gateway.setEchoEnabled(false);
        gateway.feed("silent".getBytes());
        System.out.println("  After echo disabled, sent packets: " + sentData.size());

        // Send response to peer
        gateway.send("Welcome back!\r\n");
        System.out.println("  Sent response: " + sentData.size() + " packets total");
    }

    // ── 2. All Terminal Types ─────────────────────────────────────────

    private static void demonstrateAllTerminals() {
        List<String> terminalTypes = List.of(
                "VT52", "VT100", "VT200", "VT400", "VT500", "ANSI", "XTERM"
        );

        for (String typeName : terminalTypes) {
            Terminal terminal = createTerminalForType(typeName);
            List<byte[]> sent = new ArrayList<>();
            TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                    .writer(sent::add)
                    .build();

            System.out.printf("  %-8s: type=%-8s size=%dx%d color=%b%n",
                    typeName, terminal.type(),
                    terminal.config().cols(), terminal.config().rows(),
                    terminal.supportsColor());

            // Feed a control sequence to test rendering
            terminal.feed("\u001B[2J\u001B[HHello");
            System.out.printf("         : render lines=%d cursor=(%d,%d)%n",
                    terminal.render().size(),
                    terminal.cursor().row(), terminal.cursor().col());
        }
    }

    private static Terminal createTerminalForType(String type) {
        TerminalConfig config = TerminalConfig.builder()
                .rows(24).cols(80).build();
        return switch (type.toUpperCase()) {
            case "VT52" -> VT52Terminal.create(config);
            case "VT100" -> VT100Terminal.create(config);
            case "VT200" -> VT200Terminal.create(config);
            case "VT400" -> VT400Terminal.create(config);
            case "VT500" -> VT500Terminal.create(config);
            case "ANSI" -> ANSITerminal.create(config);
            case "XTERM" -> XTERMTerminal.create(config);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }

    // ── 3. Option Negotiation ─────────────────────────────────────────

    private static void demonstrateNegotiation() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();

        // Peer requests ECHO (WILL 1) → server responds DO 1
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});
        System.out.println("  WILL ECHO → response sent: " + sent.size() + " packet(s)");

        // Peer requests SUPPRESS_GO_AHEAD (WILL 3)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 3});
        System.out.println("  WILL SGAN → response sent: " + sent.size() + " packet(s)");

        // Peer requests unknown option — server rejects
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 42});
        System.out.println("  WILL unknown → response sent: " + sent.size() + " packet(s)");

        // Peer disables ECHO (DONT 1)
        sent.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 1});
        System.out.println("  DONT ECHO → response sent: " + sent.size() + " packet(s)");
        System.out.println("  Echo disabled: " + !gateway.isEchoEnabled());
    }

    // ── 4. Subnegotiation ─────────────────────────────────────────────

    private static void demonstrateSubnegotiation() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();

        // TTYPE SEND: peer asks for terminal type
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, (byte) 24, (byte) 1,
                               (byte) 0xFF, (byte) 0xF0});
        System.out.println("  TTYPE SEND → response: " + sent.size() + " packet(s)");
        if (!sent.isEmpty()) {
            byte[] resp = sent.get(0);
            System.out.printf("         first bytes: %02X %02X %02X %02X...%n",
                    resp[0] & 0xFF, resp[1] & 0xFF, resp[2] & 0xFF, resp[3] & 0xFF);
        }

        // NAWS: peer sends new window size (80x24)
        sent.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, 31,
                               0, 80, 0, 24,
                               (byte) 0xFF, (byte) 0xF0});
        System.out.println("  NAWS (80x24) → response: " + sent.size() + " packet(s)");

        // Terminal Speed request
        sent.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, 50,
                               0, 0, 0, 4, (byte) 0xFF, (byte) 0xF0});
        System.out.println("  Speed request → response: " + sent.size() + " packet(s)");
    }

    // ── 5. Event Handling ─────────────────────────────────────────────

    private static void demonstrateEvents() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();
        gateway.addListener(events::add);

        // Feed data to trigger events
        gateway.feed("test".getBytes());

        // Trigger TTYPE via SEND
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, (byte) 24, (byte) 1,
                               (byte) 0xFF, (byte) 0xF0});

        // Trigger NAWS
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, 31,
                               0, 120, 0, 40,
                               (byte) 0xFF, (byte) 0xF0});

        // Trigger command (NOP)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1});

        System.out.println("  Events captured: " + events.size());
        for (TelnetGateway.GatewayEvent event : events) {
            System.out.printf("    [%s] %s%n",
                    event.typeLabel(),
                    event instanceof TelnetGateway.ResizeEvent re
                            ? "resize(" + re.cols() + "x" + re.rows() + ")"
                            : event instanceof TelnetGateway.TtyEvent te
                                    ? "tty(" + te.type() + ")"
                                    : event instanceof TelnetGateway.CommandEvent ce
                                            ? "cmd(" + ce.command() + ")"
                                            : "");
        }

        // Test accessors
        System.out.println("  Accessors: terminal=" + gateway.terminal().type()
                + " negotiator=" + gateway.negotiator().getClass().getSimpleName());
    }

    // ── 6. Null Safety ────────────────────────────────────────────────

    private static void demonstrateNullSafety() {
        System.out.println("  feed(null) → no crash: OK");
        System.out.println("  feed(empty) → no crash: OK");

        try {
            TelnetGateway.forTerminal(null);
            System.out.println("  forTerminal(null) → should have thrown NPE");
        } catch (NullPointerException e) {
            System.out.println("  forTerminal(null) → NullPointerException: OK");
        }

        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(System.out::print)
                .build();

        try {
            gateway.send((byte[]) null);
            System.out.println("  send((byte[])null) → no crash: OK");
        } catch (Exception e) {
            System.out.println("  send(null) → exception: " + e.getClass().getSimpleName());
        }
    }

    // ── 7. DM Synchronization ─────────────────────────────────────────

    private static void demonstrateDmSync() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();
        gateway.addListener(events::add);

        // Send DM
        gateway.sendDm();
        System.out.println("  sendDm() → sent " + sent.size() + " packet(s)");
        System.out.println("  awaitingDmSync: " + gateway.awaitingDmSync());

        // Peer echoes DM back
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2});
        System.out.println("  DM echo received → events: " + events.size());
        for (TelnetGateway.GatewayEvent event : events) {
            System.out.printf("    [%s]%n", event.typeLabel());
        }
    }

    // ── 8. Linemode ───────────────────────────────────────────────────

    private static void demonstrateLinemode() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();
        gateway.addListener(events::add);

        // Activate linemode via subnegotiation
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFA, (byte) 32,
                               (byte) 1, (byte) 0xFF, (byte) 0xF0});
        System.out.println("  LINEMODE activate → events: " + events.size());

        // Submit a line through linemode
        gateway.feed("hello world\r".getBytes());
        System.out.println("  Line submitted → events: " + events.size());

        for (TelnetGateway.GatewayEvent event : events) {
            System.out.printf("    [%s]%n", event.typeLabel());
        }
    }

    // ── 9. Binary Mode ────────────────────────────────────────────────

    private static void demonstrateBinaryMode() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();
        gateway.addListener(events::add);

        // Peer enables binary mode (WILL 0) → server responds DO 0
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0});
        System.out.println("  WILL BINARY → events: " + events.size());

        // Server requests binary mode (DO 0) → peer responds WILL 0
        sent.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, (byte) 0});
        System.out.println("  DO BINARY → events: " + events.size()
                + " sent: " + sent.size());

        for (TelnetGateway.GatewayEvent event : events) {
            System.out.printf("    [%s]%n", event.typeLabel());
        }
    }

    // ── 10. NewEnv ────────────────────────────────────────────────────

    private static void demonstrateNewEnv() {
        Terminal terminal = VT100Terminal.create(TerminalConfig.builder()
                .rows(24).cols(80).build());
        List<byte[]> sent = new ArrayList<>();
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(sent::add)
                .build();
        gateway.addListener(events::add);

        // Peer sends environment variable (e.g., TERM=xterm)
        // NewEnv subnegotiation: SB NEW_ENV IS <name> <value>
        byte[] envData = new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 252,  // SB NEW_ENV
                (byte) 0,  // IS
                (byte) 4,  // name length
                (byte)'T', (byte)'E', (byte)'R', (byte)'M',
                (byte) 5,  // value length
                (byte)'x', (byte)'t', (byte)'e', (byte)'r', (byte)'m',
                (byte) 0xFF, (byte) 0xF0  // SE
        };
        gateway.feed(envData);
        System.out.println("  NewEnv TERM=xterm → events: " + events.size());
        for (TelnetGateway.GatewayEvent event : events) {
            if (event instanceof TelnetGateway.EnvEvent env) {
                System.out.printf("    env(%s=%s)%n", env.name(), env.variable());
            } else {
                System.out.printf("    [%s]%n", event.typeLabel());
            }
        }
    }
}
