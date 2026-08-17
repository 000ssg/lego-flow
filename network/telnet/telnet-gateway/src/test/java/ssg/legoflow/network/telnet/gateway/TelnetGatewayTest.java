package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;
import ssg.legoflow.network.telnet.negotiation.BinaryHandler;
import ssg.legoflow.network.telnet.negotiation.LinemodeHandler;
import ssg.legoflow.network.telnet.negotiation.NewEnvHandler;
import ssg.legoflow.network.telnet.negotiation.OptionNegotiator;
import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive tests for {@link TelnetGateway}.
 */
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

    // ── Basic data feed ─────────────────────────────────────────────

    @Test
    void testFeedPlainTextToTerminal() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed("Hi".getBytes());

        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo("Hi".getBytes());

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

        assertThat(written).isEmpty();
    }

    @Test
    void testFeed_nullAndEmpty_noCrash() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(null);
        gateway.feed(new byte[0]);

        assertThat(written).isEmpty();
    }

    // ── Option negotiation ──────────────────────────────────────────

    @Test
    void testNegotiationResponse() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 1});

        assertThat(written).hasSize(1);
        assertThat(written.get(0)).isEqualTo(new byte[]{(byte) 0xFF, (byte) 0xFD, 1});
    }

    @Test
    void testDontEchoRejected() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 1});

        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB);
        assertThat(gateway.isEchoEnabled()).isTrue();
    }

    @Test
    void testDoEchoNegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 1});
        assertThat(gateway.isEchoEnabled()).isTrue();

        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB);
    }

    @Test
    void testDoSuppressGoAhead() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 3});
        assertThat(gateway.isSuppressGoAhead()).isTrue();

        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB);
    }

    @Test
    void testBinaryNegotiation_doAndWill() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        written.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 0}); // DO BINARY
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFB); // WILL BINARY

        written.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 0}); // WILL BINARY
        assertThat(written).hasSize(1);
        assertThat(written.get(0)[1]).isEqualTo((byte) 0xFD); // DO BINARY
        assertThat(events).contains(TelnetGateway.GatewayEvent.BINARY_NEGOTIATED);
    }

    // ── Subnegotiation ──────────────────────────────────────────────

    @Test
    void testTtypeSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 24, 1,
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(written).hasSize(1);
        byte[] response = written.get(0);
        assertThat(response[0]).isEqualTo((byte) 0xFF);
        assertThat(response[1]).isEqualTo((byte) 0xFA);
        assertThat(response[2]).isEqualTo((byte) 24);
        assertThat(response[3]).isEqualTo((byte) 0);
        assertThat(response[4]).isEqualTo((byte) 'v');
    }

    @Test
    void testNawsSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 31, (byte) 0, (byte) 132, (byte) 0, (byte) 43,
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(written).isEmpty();
        assertThat(events).contains(TelnetGateway.GatewayEvent.RESIZED);
    }

    @Test
    void testSpeedSubnegotiation() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 42, '3', '8', '4', '0', '0',
                (byte) 0xFF, (byte) 0xF0
        });
    }

    @Test
    void testNewEnvSubnegotiation() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 39, 0,
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(events).contains(TelnetGateway.GatewayEvent.ENV_EXCHANGED);
    }

    @Test
    void testNewEnvIsSubnegotiation() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 39, 1, 'T', 'E', 'R', 'M', 0, (byte) 5, 'v', 't', '1', '0', '0',
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(events).contains(TelnetGateway.GatewayEvent.ENV_EXCHANGED);
    }

    // ── Single-byte commands ────────────────────────────────────────

    @Test
    void testDmCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
        assertThat(events).contains(TelnetGateway.GatewayEvent.DM_RECEIVED);
    }

    @Test
    void testAoCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

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

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF0});
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testElCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF7}); // EL
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    @Test
    void testIpCommand() {
        var events = new ArrayList<TelnetGateway.GatewayEvent>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1}); // IP
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);
    }

    // ── Send operations ─────────────────────────────────────────────

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
    void testSend_nullAndEmpty_noCrash() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.send((byte[]) null);
        gateway.send(new byte[0]);
        assertThat(written).isEmpty();
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

    // ── feedTerminal ────────────────────────────────────────────────

    @Test
    void testFeedTerminal() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.feedTerminal("Hello".getBytes());

        List<String> lines = terminal.render();
        assertThat(lines.get(0)).startsWith("Hello");
        assertThat(written).isNotEmpty();
    }

    // ── Getters and accessors ───────────────────────────────────────

    @Test
    void testTerminalAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.terminal()).isSameAs(terminal);
    }

    @Test
    void testConnectionAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.connection()).isNotNull();
    }

    @Test
    void testNegotiatorAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.negotiator()).isNotNull();
    }

    @Test
    void testBinaryHandlerAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        BinaryHandler handler = gateway.binaryHandler();
        assertThat(handler).isNotNull();
        assertThat(handler.isLocalBinary()).isFalse();
    }

    @Test
    void testLinemodeHandlerAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        LinemodeHandler handler = gateway.linemodeHandler();
        assertThat(handler).isNotNull();
    }

    @Test
    void testNewEnvHandlerAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        NewEnvHandler handler = gateway.newEnvHandler();
        assertThat(handler).isNotNull();
    }

    // ── Environment ─────────────────────────────────────────────────

    @Test
    void testEnvGetSet() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.setEnv("SHELL", "/bin/bash");
        assertThat(gateway.getEnv("SHELL")).isEqualTo("/bin/bash");
        assertThat(gateway.getEnv("UNKNOWN")).isNull();
    }

    // ── Echo control ────────────────────────────────────────────────

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

        written.clear();
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFE, 3});
        assertThat(gateway.isSuppressGoAhead()).isTrue();
    }

    // ── Listener management ─────────────────────────────────────────

    @Test
    void testAddAndRemoveListener() {
        List<TelnetGateway.GatewayEvent> events = new ArrayList<>();
        TelnetGateway.GatewayListener listener = events::add;

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(listener);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2}); // DM fires COMMAND
        assertThat(events).contains(TelnetGateway.GatewayEvent.COMMAND);

        gateway.removeListener(listener);
        events.clear();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF5}); // AO fires COMMAND
        assertThat(events).isEmpty();
    }

    // ── Builder with custom negotiator ──────────────────────────────

    @Test
    void testBuilderWithCustomNegotiator() {
        OptionNegotiator custom = new OptionNegotiator();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .negotiator(custom)
                .build();

        assertThat(gateway.negotiator()).isSameAs(custom);
    }

    // ── Linemode ────────────────────────────────────────────────────

    @Test
    void testLinemodeSendMode() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // Default send mode is 0 (SEND_NORMAL)
        int mode = gateway.linemodeSendMode();
        assertThat(mode).isEqualTo(0);
    }

    @Test
    void testLinemodeOutputMode() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        int mode = gateway.linemodeOutputMode();
        assertThat(mode).isGreaterThanOrEqualTo(0);
    }

    @Test
    void testSendLinemodeIs() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.sendLinemodeIs();
        assertThat(written).isNotEmpty();
    }

    // ── sendDm ──────────────────────────────────────────────────────

    @Test
    void testSendDm() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        gateway.sendDm();
        // DM is queued, will be sent on next flush (via sendCommand)
        // Verify it doesn't throw
    }
}
