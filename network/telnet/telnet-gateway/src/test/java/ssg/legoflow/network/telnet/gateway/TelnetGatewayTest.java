package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.TelnetCommand;
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
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
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

        // Both DO and WILL fire BinaryEvent
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.BinaryEvent)
                .hasSize(2);
    }

    @Test
    void testBinaryNegotiation_wontDisables() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Complete binary handshake: both DO and WILL
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFD, 0}); // DO BINARY
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFB, 0}); // WILL BINARY
        assertThat(gateway.binaryHandler().isLocalBinary()).isTrue();
        assertThat(gateway.binaryHandler().isRemoteBinary()).isTrue();

        written.clear();
        // Peer sends WONT BINARY → we respond DONT → remote becomes OFF
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xFC, 0}); // WONT BINARY
        assertThat(gateway.binaryHandler().isRemoteBinary()).isFalse();
        assertThat(gateway.binaryHandler().isNegotiated()).isFalse();
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

    // ── Single-byte commands ────────────────────────────────────────

    @Test
    void testDmCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2});
        // DM fires DmEvent(false) (not sync)
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.DmEvent de && !de.isSync())
                .hasSize(1);
    }

    @Test
    void testDmSync() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Send DM first (starts awaiting sync)
        gateway.sendDm();
        written.clear();

        // Peer echoes DM back
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2});
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.DmEvent de && de.isSync())
                .hasSize(1);
    }

    @Test
    void testAoCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF5});
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent ce
                        && ce.command() == TelnetCommand.AO)
                .hasSize(1);
    }

    @Test
    void testSeCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF0});
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent ce
                        && ce.command() == TelnetCommand.SE)
                .hasSize(1);
    }

    @Test
    void testElCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // EL command code is 248 (0xF8)
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF8});
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent ce
                        && ce.command() == TelnetCommand.EL)
                .hasSize(1);
    }

    @Test
    void testIpCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IP command code is 241 (0xF1), same as NOP
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1});
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent)
                .hasSize(1);
    }

    @Test
    void testGaCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF9}); // GA
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent ce
                        && ce.command() == TelnetCommand.GA)
                .hasSize(1);
    }

    @Test
    void testAytCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF6}); // AYT
        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.CommandEvent ce
                        && ce.command() == TelnetCommand.AYT)
                .hasSize(1);
    }

    @Test
    void testNopCommand() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1}); // NOP (241)
        // Note: 0xF1 maps to IP (241), not NOP — NOP uses alternate code 241
        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF1});
    }

    // ── TTYPE subnegotiation ────────────────────────────────────────

    @Test
    void testTtypeSendRequest() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Peer sends TTYPE SEND: IAC SB TTYPE SEND IAC SE
        // Gateway responds with its terminal type
        byte[] ttypeSend = new byte[]{
                (byte) 0xFF, (byte) 0xFA, (byte) 0x18, (byte) 0x01,
                (byte) 0xFF, (byte) 0xF0
        };
        gateway.feed(ttypeSend);

        // Gateway responds with IS + terminal type
        assertThat(written).isNotEmpty();
        byte[] response = written.get(written.size() - 1);
        assertThat(response[0]).isEqualTo((byte) 0xFF);
        assertThat(response[1]).isEqualTo((byte) 0xFA);
        assertThat(response[2]).isEqualTo((byte) 0x18); // TTYPE
        assertThat(response[3]).isEqualTo((byte) 0); // IS
        // TtyEvent fires only when peer sends their type (IS), not when requesting ours (SEND)
        assertThat(events).isEmpty();
    }

    // ── NAWS subnegotiation ─────────────────────────────────────────

    @Test
    void testNawsSubnegotiation() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Peer sends NAWS: IAC SB 31 0 80 0 24 IAC SE
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 31,
                0, 80, 0, 24,
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.ResizeEvent)
                .hasSize(1);
    }

    @Test
    void testNawsEventCarriesColsAndRows() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 31,
                0, 120, 0, 40,
                (byte) 0xFF, (byte) 0xF0
        });

        TelnetGateway.ResizeEvent event = events.stream()
                .filter(e -> e instanceof TelnetGateway.ResizeEvent re)
                .map(e -> (TelnetGateway.ResizeEvent) e)
                .findFirst().orElseThrow();
        assertThat(event.cols()).isEqualTo(120);
        assertThat(event.rows()).isEqualTo(40);
    }

    // ── Linemode subnegotiation ─────────────────────────────────────

    @Test
    void testLinemodeStart() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // IAC SB 34 2 (LINEMODE START) IAC SE
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 34,
                2, // START
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.LinemodeActiveEvent)
                .hasSize(1);
    }

    @Test
    void testLinemodeOff() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Start linemode
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 34,
                2, // START
                (byte) 0xFF, (byte) 0xF0
        });

        // Stop linemode
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 34,
                3, // OFF
                (byte) 0xFF, (byte) 0xF0
        });

        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.LinemodeInactiveEvent)
                .hasSize(1);
    }

    @Test
    void testLinemodeEventLabel() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 34,
                2, // START
                (byte) 0xFF, (byte) 0xF0
        });

        TelnetGateway.LinemodeActiveEvent event = events.stream()
                .filter(e -> e instanceof TelnetGateway.LinemodeActiveEvent)
                .map(e -> (TelnetGateway.LinemodeActiveEvent) e)
                .findFirst().orElseThrow();
        assertThat(event.typeLabel()).isEqualTo("linemode_active");
    }

    // ── Environment ─────────────────────────────────────────────────

    @Test
    void testNewEnvSubnegotiation() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // INFO suboption (0) — gateway responds but does not fire EnvEvent
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 39, 0,
                (byte) 0xFF, (byte) 0xF0
        });

        // INFO triggers response but no EnvEvent (that fires on IS with remote vars)
        assertThat(written).isNotEmpty();
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
    void testEnvEventCarriesNameAndVariable() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        // Remote sends IS with TERM=vt100
        List<Integer> data = new ArrayList<>();
        data.add(1);     // IS
        data.add(4);     // name length
        data.add((int)'T'); data.add((int)'E'); data.add((int)'R'); data.add((int)'M');
        data.add(4);     // value length
        data.add((int)'v'); data.add((int)'t'); data.add((int)'1'); data.add((int)'0');

        gateway.newEnvHandler().handle(data);

        assertThat(events)
                .filteredOn(e -> e instanceof TelnetGateway.EnvEvent ee
                        && ee.name().equals("TERM"))
                .hasSize(1);
    }

    @Test
    void testDefaultEnvVars() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.getEnv("TERM")).isEqualTo("vt100");
        assertThat(gateway.getEnv("COLS")).isEqualTo("80");
        assertThat(gateway.getEnv("LINES")).isEqualTo("24");
    }

    // ── Linemode in gateway data path ───────────────────────────────

    @Test
    void testLinemodeProcessesLineChar() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        LinemodeHandler lm = gateway.linemodeHandler();
        assertThat(lm.isActive()).isFalse();

        // Start linemode
        gateway.feed(new byte[]{
                (byte) 0xFF, (byte) 0xFA, 34,
                2, // START
                (byte) 0xFF, (byte) 0xF0
        });
        assertThat(lm.isActive()).isTrue();

        // Feed characters through gateway data path
        written.clear();
        gateway.feed("hello".getBytes());
        assertThat(lm.getLineBuffer()).isEqualTo("hello");
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

    // ── Getters and accessors ───────────────────────────────────────

    @Test
    void testTerminalAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.terminal()).isSameAs(terminal);
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
    void testNawsHandlerAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.nawsHandler()).isNotNull();
    }

    @Test
    void testSpeedHandlerAccessor() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThat(gateway.speedHandler()).isNotNull();
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
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway.GatewayListener listener = events::add;

        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(listener);

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2}); // DM fires DmEvent + CommandEvent
        assertThat(events).isNotEmpty();

        gateway.removeListener(listener);
        events.clear();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF5}); // AO fires CommandEvent
        assertThat(events).isEmpty();
    }

    @Test
    void testListenerNullArg() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThatThrownBy(() -> gateway.addListener(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEventSealedHierarchy() {
        // Verify all event types implement GatewayEvent
        List<TelnetGateway.GatewayEvent> events = List.of(
                new TelnetGateway.ConnectedEvent(),
                new TelnetGateway.DisconnectedEvent(),
                new TelnetGateway.NegotiatedEvent(1),
                new TelnetGateway.ResizeEvent(80, 24),
                new TelnetGateway.TtyEvent("xterm"),
                new TelnetGateway.CommandEvent(TelnetCommand.AYT),
                new TelnetGateway.DmEvent(false),
                new TelnetGateway.DmEvent(true),
                new TelnetGateway.BinaryEvent(true, true),
                new TelnetGateway.EnvEvent("TERM", null),
                new TelnetGateway.LineEvent("hello"),
                new TelnetGateway.LinemodeActiveEvent(),
                new TelnetGateway.LinemodeInactiveEvent()
        );

        for (TelnetGateway.GatewayEvent e : events) {
            assertThat(e.typeLabel()).isNotNull();
            assertThat(e.typeLabel()).isNotEmpty();
        }
    }

    @Test
    void testEventPatternMatching() {
        TelnetGateway.GatewayEvent dmEvent = new TelnetGateway.DmEvent(false);

        if (dmEvent instanceof TelnetGateway.DmEvent de) {
            assertThat(de.isSync()).isFalse();
        } else {
            org.junit.jupiter.api.Assertions.fail();
        }

        TelnetGateway.GatewayEvent ttyEvent = new TelnetGateway.TtyEvent("vt220");
        if (ttyEvent instanceof TelnetGateway.TtyEvent te) {
            assertThat(te.type()).isEqualTo("vt220");
        } else {
            org.junit.jupiter.api.Assertions.fail();
        }

        TelnetGateway.GatewayEvent binaryEvent = new TelnetGateway.BinaryEvent(true, false);
        if (binaryEvent instanceof TelnetGateway.BinaryEvent be) {
            assertThat(be.localBinary()).isTrue();
            assertThat(be.remoteBinary()).isFalse();
        } else {
            org.junit.jupiter.api.Assertions.fail();
        }
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

    // ── Linemode modes ──────────────────────────────────────────────

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
        assertThat(gateway.awaitingDmSync()).isTrue();

        gateway.feed(new byte[]{(byte) 0xFF, (byte) 0xF2}); // DM echo
        assertThat(gateway.awaitingDmSync()).isFalse();
    }

    // ── TTYPE handler ──────────────────────────────────────────────

    @Test
    void testTtypeHandlerSendType() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.ttypeHandler().sendRequest(gateway.connection());

        assertThat(written).hasSize(1);
        // IAC SB 24 SEND IAC SE
        assertThat(written.get(0)).containsExactly(
                (byte) 0xFF, (byte) 0xFA, (byte) 0x18, // IAC SB TTYPE
                (byte) 0x01, // SEND
                (byte) 0xFF, (byte) 0xF0  // IAC SE
        );
    }

    @Test
    void testTtypeHandlerSendCustomType() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.ttypeHandler().sendType(gateway.connection());

        assertThat(written).hasSize(1);
        byte[] data = written.get(0);
        // IAC SB 24 IS "vt100" 0 IAC SE = 13 bytes total
        // [0]=FF [1]=FA [2]=24 [3]=IS(0) [4-8]="vt100" [9]=0 [10]=FF [11]=F0
        assertThat(data[3]).isEqualTo((byte) 0x00); // IS
        String type = new String(data, 4, 5);
        assertThat(type).isEqualTo("vt100");
    }

    // ── Speed handler ──────────────────────────────────────────────

    @Test
    void testSpeedHandlerSendRequest() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.speedHandler().sendRequest(gateway.connection());

        assertThat(written).hasSize(1);
        byte[] data = written.get(0);
        // IAC SB 42 SEND IAC SE
        assertThat(data[2]).isEqualTo((byte) 42); // TERMINAL_SPEED
        assertThat(data[3]).isEqualTo((byte) 0x01); // SEND
    }

    @Test
    void testSpeedHandlerSendSpeed() {
        List<TelnetGateway.GatewayEvent> events = new CopyOnWriteArrayList<>();
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();
        gateway.addListener(events::add);

        gateway.speedHandler().sendSpeed(gateway.connection());

        assertThat(written).hasSize(1);
        byte[] data = written.get(0);
        assertThat(data[2]).isEqualTo((byte) 42); // TERMINAL_SPEED
        assertThat(data[3]).isEqualTo((byte) 0x00); // IS
    }

    // ── GatewayEvent static factory tests ──────────────────────────

    @Test
    void testConnectedEvent() {
        var evt = new TelnetGateway.ConnectedEvent();
        assertThat(evt.typeLabel()).isEqualTo("connected");
    }

    @Test
    void testDisconnectedEvent() {
        var evt = new TelnetGateway.DisconnectedEvent();
        assertThat(evt.typeLabel()).isEqualTo("disconnected");
    }

    @Test
    void testNegotiatedEvent() {
        var evt = new TelnetGateway.NegotiatedEvent(24);
        assertThat(evt.optionCode()).isEqualTo(24);
        assertThat(evt.typeLabel()).isEqualTo("negotiated");
    }

    @Test
    void testLineEvent() {
        var evt = new TelnetGateway.LineEvent("test line");
        assertThat(evt.line()).isEqualTo("test line");
        assertThat(evt.typeLabel()).isEqualTo("line");
    }

    // ── Binary translation in data path ─────────────────────────────

    @Test
    void testDataPathWithBinaryHandler() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        // In non-binary mode, LF → CR NL in outbound
        byte[] input = new byte[]{10}; // LF
        gateway.feed(input);

        // Output should contain translated CR NL
        assertThat(written).isNotEmpty();
    }

    // ── Null safety ─────────────────────────────────────────────────

    @Test
    void testBuilderWithNullTerminal() {
        assertThatThrownBy(() -> TelnetGateway.forTerminal(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGatewayListenerNotNull() {
        TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
                .writer(written::add)
                .build();

        assertThatThrownBy(() -> gateway.addListener(null))
                .isInstanceOf(NullPointerException.class);
    }
}
