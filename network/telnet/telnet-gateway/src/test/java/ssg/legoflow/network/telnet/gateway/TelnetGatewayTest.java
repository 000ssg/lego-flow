package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TelnetGatewayTest {

    private Terminal terminal;
    private List<byte[]> written;

    @BeforeEach
    void setUp() {
        TerminalConfig config = TerminalConfig.builder()
                .rows(24).cols(80).build();
        terminal = VT100Terminal.create(config);
        written = new ArrayList<>();
    }

    @Test
    void testFeedPlainTextToTerminal() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Feed plain text "Hi" to the gateway
        gateway.feed("Hi".getBytes());

        // Echo should be sent back (default: echo on)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("Hi".getBytes());

        // Terminal should have the text
        List<String> lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hi");
    }

    @Test
    void testEchoCanBeDisabled() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.setEchoEnabled(false);
        gateway.feed("test".getBytes());

        // No echo
        assertThat(written).isEmpty();
    }

    @Test
    void testNegotiationResponse() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC WILL ECHO (255 251 1)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});

        // Gateway responds with IAC DO ECHO (255 253 1)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFD, 1});
    }

    @Test
    void testDontEchoRejected() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC DONT ECHO — gateway responds WILL (keeps echo enabled)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 1});

        // Gateway responds with IAC WILL ECHO (keeps echo)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB); // WILL
        assertThat(gateway.isEchoEnabled()).isTrue();
    }

    @Test
    void testTtypeSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC SB 24 1 IAC SE (TTYPE SEND)
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 24, 1,
                (byte) 0xFF, (byte) 0xF0
        });

        // Gateway responds with IAC SB 24 0 "vt100\0" IAC SE (TTYPE IS)
        assertThat(written).hasSize(1);
        byte[] response = written.get(0);
        assertThat(response[0]).isEqualTo((byte) 0xFF); // IAC
        assertThat(response[1]).isEqualTo((byte) 0xFA); // SB
        assertThat(response[2]).isEqualTo((byte) 24);   // TTYPE
        assertThat(response[3]).isEqualTo((byte) 0);    // IS
        assertThat(response[4]).isEqualTo((byte) 'v');
        assertThat(response[response.length - 2]).isEqualTo((byte) 0xFF); // IAC
        assertThat(response[response.length - 1]).isEqualTo((byte) 0xF0); // SE
    }

    @Test
    void testNawsSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends NAWS: 132 cols, 43 rows
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 31, (byte) 0, (byte) 132, (byte) 0, (byte) 43,
                (byte) 0xFF, (byte) 0xF0
        });

        // No response expected for NAWS
        assertThat(written).isEmpty();
    }

    @Test
    void testSendToPeer() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send("hello");
        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("hello".getBytes());
    }

    @Test
    void testSendEscapesIac() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send(new byte[]{0x01, (byte) 0xFF, 0x02});
        assertThat(written).hasSize(1);
        assertThat(written.get(0))
                .isEqualTo(new byte[]{0x01, (byte) 0xFF, (byte) 0xFF, 0x02});
    }

    @Test
    void testTerminalAccess() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.terminal()).isSameAs(terminal);
        assertThat(gateway.terminal().type()).isEqualTo("vt100");
    }

    // --- Single-byte commands ---

    @Test
    void testGaCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // GA (Go Ahead) — IAC GA = 255 249
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF8});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testEraseCharCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // EC (Erase Character) — IAC EC = 255 247
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF7});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testEraseLineCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // EL (Erase Line) — IAC EL = 255 246
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF6});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testAytCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // AYT (Are You There) — IAC AYT = 255 249
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF9});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testInterruptProcessCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IP (Interrupt Process) — IAC IP = 255 244
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF4});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testNopCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // NOP — IAC NOP = 255 241 (alternate code; RFC 254 collides with DONT)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    // --- feedTerminal ---

    @Test
    void testFeedTerminal() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Feed terminal directly (bypasses Telnet parser)
        gateway.feedTerminal("Direct text".getBytes());
        var lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Direct");
    }

    // --- Event listeners ---

    @Test
    void testAddRemoveListener() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        var listener = (TelnetGateway.GatewayListener) event -> {};
        gateway.addListener(listener);
        gateway.removeListener(listener);
    }

    @Test
    void testListenerFiresBinaryNegotiated() {
        var fired = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(fired::add);

        // Trigger a BINARY negotiation event
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 0}); // DO BINARY
        assertThat(fired).contains(TelnetGateway.GatewayEvent.BINARY_NEGOTIATED);
    }

    // --- SUPPRESS_GO_AHEAD negotiation ---

    @Test
    void testDontSuppressGoAheadRejected() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC DONT SUPPRESS_GO_AHEAD — gateway responds WILL
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 3});

        // Gateway responds with IAC WILL SUPPRESS_GO_AHEAD
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB); // WILL
        assertThat(gateway.isSuppressGoAhead()).isTrue();
    }

    // --- Unknown option negotiation ---

    @Test
    void testUnknownOptionNegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Peer sends IAC WILL for unknown option 200
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 200});

        // Gateway responds with IAC DONT (negotiator rejects unknown)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFE); // DONT (254)
        assertThat(written.get(0)[2]).isEqualTo((byte) 200);
    }

    // --- BINARY subnegotiation (rare) ---

    @Test
    void testBinarySubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // IAC SB 0 1 2 3 IAC SE — BINARY subnegotiation
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 0, 1, 2, 3,
                (byte) 0xFF, (byte) 0xF0
        });

        // BINARY subnegotiation is rare; should not crash
        assertThat(written).isEmpty();
    }

    // --- Additional coverage tests ---

    @Test
    void testSetEchoEnabled() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.isEchoEnabled()).isTrue();
        gateway.setEchoEnabled(false);
        assertThat(gateway.isEchoEnabled()).isFalse();
        gateway.setEchoEnabled(true);
        assertThat(gateway.isEchoEnabled()).isTrue();
    }

    @Test
    void testSetSuppressGoAhead() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.isSuppressGoAhead()).isTrue();
        
        // Peer sends IAC DONT SUPPRESS_GO_AHEAD — gateway keeps it enabled
        written.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 3});
        assertThat(gateway.isSuppressGoAhead()).isTrue(); // Gateway keeps SG enabled
    }

    @Test
    void testEnvGetSet() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.setEnv("SHELL", "/bin/bash");
        assertThat(gateway.getEnv("SHELL")).isEqualTo("/bin/bash");
        assertThat(gateway.getEnv("UNKNOWN")).isNull();
    }

    @Test
    void testDmCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IAC DM = 255 242
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testAoCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IAC AO = 255 245
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF5});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testSeCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IAC SE = 255 240 (outside subnegotiation, treated as single-byte command)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF0});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testDoEchoNegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // DO ECHO — gateway responds WILL (0xFB=251=WILL)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 1}); // DO ECHO (0xFD=253)
        assertThat(gateway.isEchoEnabled()).isTrue();
        
        // Verify response: IAC WILL ECHO (255 251 1)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB); // WILL
    }

    @Test
    void testSpeedSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // IAC SB TERMINAL_SPEED "38400" IAC SE
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 42, '3', '8', '4', '0', '0',
                (byte) 0xFF, (byte) 0xF0
        });

        // Gateway sends speed response
        // No crash — just verify it handles the subnegotiation
    }

    @Test
    void testDoSuppressGoAhead() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // DO SUPPRESS_GO_AHEAD — gateway responds WILL
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 3}); // DO SUPPRESS_GO_AHEAD (0xFD=253)
        assertThat(gateway.isSuppressGoAhead()).isTrue();
        
        // Verify response: IAC WILL SUPPRESS_GO_AHEAD (255 251 3)
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB); // WILL
    }

    @Test
    void testSendString() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send("Hello");
        assertThat(written).hasSize(1);
        assertThat(new String(written.get(0))).isEqualTo("Hello");
    }

    @Test
    void testNewEnvSubnegotiation() {
        var envEvents = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(envEvents::add);

        // IAC SB NEW_ENV INFO IAC SE — request environment
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 39, 0,
                (byte) 0xFF, (byte) 0xF0
        });

        // Gateway should respond with IS subnegotiation and fire ENV_EXCHANGED
        assertThat(envEvents).contains(TelnetGateway.GatewayEvent.ENV_EXCHANGED);
    }

}
