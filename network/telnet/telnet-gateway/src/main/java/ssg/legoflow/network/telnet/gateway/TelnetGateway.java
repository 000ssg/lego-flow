package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.*;
import ssg.legoflow.network.telnet.negotiation.*;
import ssg.legoflow.network.terminals.base.io.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Bridges a Telnet connection to a terminal emulator.
 *
 * <p>The gateway sits between the network transport and the terminal:
 * <ul>
 *   <li><b>Inbound (peer → terminal)</b>: Parses Telnet protocol from the peer,
 *       strips IAC commands, performs byte translation (RFC 856), and feeds
 *       clean application data to the terminal.</li>
 *   <li><b>Outbound (terminal → peer)</b>: Renders terminal output, applies
 *       byte translation, and sends back with IAC escaping (RFC 854).</li>
 * </ul>
 *
 * <p>Option negotiation is handled automatically:
 * <ul>
 *   <li>ECHO — enabled by default (server echoes input)</li>
 *   <li>SUPPRESS_GO_AHEAD — enabled by default</li>
 *   <li>TTYPE — responds with the terminal type</li>
 *   <li>NAWS — updates terminal dimensions on resize</li>
 *   <li>BINARY — enables 8-bit binary transmission with byte translation (RFC 856)</li>
 *   <li>LINEMODE — full line discipline with editing (RFC 1143)</li>
 *   <li>NEW_ENV — provides TERM/COLS/LINES environment with INFOMASK support (RFC 1408)</li>
 * </ul>
 *
 * <p>DM (Data Mark) synchronization is supported per RFC 854:
 * receive DM and echo it back, with DM_RECEIVED and DM_SYNC events.
 *
 * <p>Event model:
 * <p>All gateway events are expressed as a sealed interface hierarchy
 * ({@link GatewayEvent}), with each event type carrying relevant data as a record.
 * This replaces the previous enum-based event model to allow richer, type-safe events.
 *
 * <p>Usage:
 * <pre>{@code
 * Terminal terminal = VT100Terminal.create(config);
 * TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
 *         .writer(socket::write)
 *         .build();
 *
 * gateway.addListener(event -> {
 *     if (event instanceof GatewayEvent.ResizeEvent e) {
 *         System.out.println("Resized to " + e.cols() + "x" + e.rows());
 *     } else if (event instanceof GatewayEvent.TtyEvent e) {
 *         System.out.println("Terminal type: " + e.type());
 *     }
 * });
 *
 * // Feed data from socket
 * gateway.feed(socket.read());
 * }</pre>
 *
 * @since 0.2.0
 */
public class TelnetGateway {

    private final Terminal terminal;
    private final TelnetConnection connection;
    private final OptionNegotiator negotiator;
    private final TTYPEHandler ttypeHandler;
    private final NAWSHandler nawsHandler;
    private final SpeedHandler speedHandler;
    private final BinaryHandler binaryHandler;
    private final LinemodeHandler linemodeHandler;
    private final NewEnvHandler newEnvHandler;
    private final CopyOnWriteArrayList<GatewayListener> listeners;
    private boolean echoEnabled = true;
    private boolean suppressGoAhead = true;
    private boolean awaitingDmSync;

    /**
     * Listener for gateway events.
     *
     * <p>Events are typed records implementing {@link GatewayEvent}.
     * Use instanceof pattern matching to destructure event data.
     */
    @FunctionalInterface
    public interface GatewayListener {
        void onEvent(GatewayEvent event);
    }

    /**
     * Sealed interface for all gateway events.
     *
     * <p>Each subtype carries structured data for its event type,
     * replacing the previous flat enum with a rich, type-safe hierarchy.
     */
    public sealed interface GatewayEvent
            permits ConnectedEvent, DisconnectedEvent, NegotiatedEvent,
                    ResizeEvent, TtyEvent, CommandEvent, DmEvent,
                    BinaryEvent, EnvEvent, LineEvent,
                    LinemodeActiveEvent, LinemodeInactiveEvent {

        /** A short human-readable label for this event. */
        String typeLabel();
    }

    /** Connection established. */
    public static record ConnectedEvent() implements GatewayEvent {
        public String typeLabel() { return "connected"; }
    }

    /** Connection closed. */
    public static record DisconnectedEvent() implements GatewayEvent {
        public String typeLabel() { return "disconnected"; }
    }

    /** Option negotiation completed. */
    public static record NegotiatedEvent(int optionCode) implements GatewayEvent {
        public String typeLabel() { return "negotiated"; }
    }

    /** Terminal resized via NAWS. */
    public static record ResizeEvent(int cols, int rows) implements GatewayEvent {
        public String typeLabel() { return "resized"; }
    }

    /** Terminal type exchanged via TTYPE. */
    public static record TtyEvent(String type) implements GatewayEvent {
        public String typeLabel() { return "tty"; }
    }

    /** Single-byte Telnet command received from peer. */
    public static record CommandEvent(TelnetCommand command) implements GatewayEvent {
        public String typeLabel() { return "command"; }
    }

    /** Data Mark received or echoed. */
    public static record DmEvent(boolean isSync) implements GatewayEvent {
        public String typeLabel() { return isSync ? "dm_sync" : "dm_received"; }
    }

    /** Binary mode negotiated (local or remote). */
    public static record BinaryEvent(boolean localBinary, boolean remoteBinary) implements GatewayEvent {
        public String typeLabel() { return "binary"; }
    }

    /** Environment variable exchanged. */
    public static record EnvEvent(String name, NewEnvHandler.EnvVar variable) implements GatewayEvent {
        public String typeLabel() { return "env"; }
    }

    /** Line submitted via LINEMODE. */
    public static record LineEvent(String line) implements GatewayEvent {
        public String typeLabel() { return "line"; }
    }

    /** LINEMODE activated. */
    public static record LinemodeActiveEvent() implements GatewayEvent {
        public String typeLabel() { return "linemode_active"; }
    }

    /** LINEMODE deactivated. */
    public static record LinemodeInactiveEvent() implements GatewayEvent {
        public String typeLabel() { return "linemode_inactive"; }
    }

    private TelnetGateway(Builder builder) {
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must not be null");
        this.negotiator = builder.negotiator != null ? builder.negotiator : new GatewayNegotiator();
        this.listeners = new CopyOnWriteArrayList<>();
        this.awaitingDmSync = false;

        String termType = terminal.type();
        this.ttypeHandler = TTYPEHandler.localType(termType)
                .onRemoteType(type -> fire(new TtyEvent(type)));

        this.nawsHandler = NAWSHandler.localSize(terminal.config().cols(), terminal.config().rows())
                .onRemoteSize((cols, rows) -> fire(new ResizeEvent(cols, rows)));

        this.speedHandler = SpeedHandler.localSpeed("38400");

        this.binaryHandler = BinaryHandler.create();

        this.linemodeHandler = LinemodeHandler.create()
                .onLineSubmitted(line -> {
                    fire(new LineEvent(line));
                    // Submit the line to the terminal as typed input followed by CR
                    terminal.feed((line + "\r").getBytes());
                    List<String> rendered = terminal.render();
                    if (!rendered.isEmpty()) {
                        String output = String.join("\r\n", rendered);
                        send(output);
                    }
                });

        this.newEnvHandler = NewEnvHandler.create(termType, terminal.config().cols(), terminal.config().rows())
                .onRemoteVar((name, variable) -> fire(new EnvEvent(name, variable)));

        this.connection = TelnetConnection.builder()
                .writer(builder.writer)
                .onData(this::handleData)
                .onCommand(this::handleCommand)
                .onNegotiate(this::handleNegotiate)
                .onSubnegotiation(evt -> handleSubnegotiation(evt.option(), evt.data()))
                .build();
    }

    /**
     * Create a builder for a gateway wrapping the given terminal.
     */
    public static Builder forTerminal(Terminal terminal) {
        if (terminal == null) throw new NullPointerException("terminal must not be null");
        return new Builder(terminal);
    }

    /** Feed bytes from the remote peer. */
    public void feed(byte[] data) {
        if (data == null || data.length == 0) return;
        connection.feed(data);
        connection.flush();
    }

    /** Send data to the remote peer (IAC auto-escaped). */
    public void send(byte[] data) {
        if (data == null || data.length == 0) return;
        connection.send(data);
    }

    /** Send a string to the remote peer (UTF-8, IAC auto-escaped). */
    public void send(String text) {
        connection.send(text);
    }

    /**
     * Send a Data Mark (DM) per RFC 854.
     *
     * <p>The peer should echo the DM back; DM_SYNC event fires when received.
     */
    public void sendDm() {
        connection.sendCommand(TelnetCommand.DM);
        awaitingDmSync = true;
    }

    /**
     * Check if the gateway is currently awaiting a DM echo.
     */
    public boolean awaitingDmSync() {
        return awaitingDmSync;
    }

    /**
     * Check if echo is enabled (server echoes input back to peer).
     */
    public boolean isEchoEnabled() {
        return echoEnabled;
    }

    /**
     * Enable or disable echo (RFC 857).
     */
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }

    /**
     * Check if suppress go-ahead is enabled.
     */
    public boolean isSuppressGoAhead() {
        return suppressGoAhead;
    }

    /**
     * Enable or disable suppress go-ahead (RFC 858).
     */
    public void setSuppressGoAhead(boolean enabled) {
        this.suppressGoAhead = enabled;
    }

    /**
     * Get the underlying option negotiator.
     */
    public OptionNegotiator negotiator() {
        return negotiator;
    }

    /**
     * Get the TTYPE handler.
     */

    /** Get the terminal used by this gateway. */
    public Terminal terminal() {
        return terminal;
    }

    /** Get the underlying TelnetConnection. */
    public TelnetConnection connection() {
        return connection;
    }

    public TTYPEHandler ttypeHandler() {
        return ttypeHandler;
    }

    /**
     * Get the NAWS handler.
     */
    public NAWSHandler nawsHandler() {
        return nawsHandler;
    }

    /**
     * Get the speed handler.
     */
    public SpeedHandler speedHandler() {
        return speedHandler;
    }

    /**
     * Get the binary transmission handler.
     */
    public BinaryHandler binaryHandler() {
        return binaryHandler;
    }

    /**
     * Get the linemode handler.
     */
    public LinemodeHandler linemodeHandler() {
        return linemodeHandler;
    }

    /**
     * Get the new environment handler.
     */
    public NewEnvHandler newEnvHandler() {
        return newEnvHandler;
    }

    /** Get an environment variable value by name. */
    public String getEnv(String name) {
        NewEnvHandler.EnvVar var = newEnvHandler.getEnvironment().get(name);
        return var != null ? var.value() : null;
    }

    /** Set an environment variable. */
    public void setEnv(String name, String value) {
        newEnvHandler.set(name, value);
    }

    /** Get linemode send mode. */
    public int linemodeSendMode() {
        return linemodeHandler.getSendMode();
    }

    /** Get linemode output mode. */
    public int linemodeOutputMode() {
        return linemodeHandler.getOutputMode();
    }

    /**
     * Send LINEMODE IS to the peer.
     */
    public void sendLinemodeIs() {
        byte[] isData = linemodeHandler.buildIsResponse();
        connection.sendSubnegotiation(TelnetOption.LINEMODE.code(), isData);
    }

    // --- Event firing ---

    private void fire(GatewayEvent event) {
        for (GatewayListener listener : listeners) {
            listener.onEvent(event);
        }
    }

    /** Add a gateway listener. */
    public void addListener(GatewayListener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    /** Remove a gateway listener. */
    public void removeListener(GatewayListener listener) {
        listeners.remove(listener);
    }

    // --- Protocol handlers ---

    private void handleData(byte[] data) {
        // Application data from parser — apply binary translation,
        // echo back to peer if echo enabled, then feed to terminal.
        byte[] translated = binaryHandler.translateInbound(data);

        if (linemodeHandler.isActive()) {
            for (byte b : translated) {
                char ch = (char) (b & 0xFF);
                boolean consumed = linemodeHandler.processLineChar(ch);
                if (!consumed && echoEnabled) {
                    byte[] outbound = binaryHandler.translateOutbound(new byte[]{b});
                    connection.send(outbound);
                }
            }
        } else {
            // Echo data back to peer in character mode (RFC 854)
            if (echoEnabled) {
                connection.send(translated);
            }
            terminal.feed(translated);
        }
    }

    /** Handle single-byte Telnet commands. */
    private void handleCommand(TelnetCommand command) {
        switch (command) {
            case DM -> {
                // RFC 854: Echo DM back immediately
                connection.sendCommand(TelnetCommand.DM);
                fire(new DmEvent(false));

                if (awaitingDmSync) {
                    awaitingDmSync = false;
                    fire(new DmEvent(true));
                }
            }
            case BRK, GA, EC, EL, AYT, IP, NOP, AO, SE ->
                fire(new CommandEvent(command));
            default -> {}
        }
    }

    /** Handle option negotiation (WILL/WONT/DO/DONT). */
    private void handleNegotiate(TelnetCommand command, int option) {
        TelnetCommand response = negotiator.negotiate(command, option);
        connection.sendNegotiate(response, option);

        TelnetOption opt = TelnetOption.fromCode(option);
        if (opt == null) return;

        switch (opt) {
            case ECHO -> {
                echoEnabled = response == TelnetCommand.WILL;
            }
            case SUPPRESS_GO_AHEAD -> {
                suppressGoAhead = response == TelnetCommand.WILL;
            }
            case BINARY -> {
                if (command == TelnetCommand.DO && response == TelnetCommand.WILL) {
                    binaryHandler.setLocalBinary(true);
                    fire(new BinaryEvent(true, binaryHandler.isRemoteBinary()));
                } else if (command == TelnetCommand.DONT && response == TelnetCommand.WONT) {
                    binaryHandler.setLocalBinary(false);
                    fire(new BinaryEvent(false, binaryHandler.isRemoteBinary()));
                } else if (command == TelnetCommand.WILL && response == TelnetCommand.DO) {
                    binaryHandler.setRemoteBinary(true);
                    fire(new BinaryEvent(binaryHandler.isLocalBinary(), true));
                } else if (command == TelnetCommand.WONT && response == TelnetCommand.DONT) {
                    binaryHandler.setRemoteBinary(false);
                    fire(new BinaryEvent(binaryHandler.isLocalBinary(), false));
                }
            }
            case LINEMODE -> {
                // Linemode negotiation handled via subnegotiation
            }
            case NEW_ENV -> {
                // New environment handled via subnegotiation
            }
            default -> {}
        }
    }

    /** Handle subnegotiation data. */
    private void handleSubnegotiation(int option, List<Integer> data) {
        TelnetOption opt = TelnetOption.fromCode(option);
        if (opt == null) return;

        switch (opt) {
            case TTYPE -> {
                byte[] ttypeResponse = ttypeHandler.handle(data);
                if (ttypeResponse != null) {
                    connection.sendSubnegotiation(option, ttypeResponse);
                }
            }
            case NAWS -> nawsHandler.handle(data);
            case TERMINAL_SPEED -> {
                byte[] speedResponse = speedHandler.handle(data);
                if (speedResponse != null) {
                    connection.sendSubnegotiation(option, speedResponse);
                }
            }
            case LINEMODE -> {
                byte[] lmResponse = linemodeHandler.handle(data);
                if (lmResponse != null) {
                    connection.sendSubnegotiation(option, lmResponse);
                }
                // Update events based on linemode state
                if (linemodeHandler.isActive()) {
                    fire(new LinemodeActiveEvent());
                } else {
                    fire(new LinemodeInactiveEvent());
                }
            }
            case NEW_ENV -> {
                byte[] envResponse = newEnvHandler.handle(data);
                if (envResponse != null) {
                    connection.sendSubnegotiation(option, envResponse);
                }
            }
            case BINARY -> {
                // BINARY subnegotiation is unusual; handle as state tracking
            }
            default -> {}
        }
    }

    // --- Builder ---

    /** Builder for {@link TelnetGateway}. */
    public static class Builder {
        private final Terminal terminal;
        private Consumer<byte[]> writer;
        private OptionNegotiator negotiator;

        Builder(Terminal terminal) {
            this.terminal = terminal;
        }

        /** Set the writer for sending bytes to the peer. */
        public Builder writer(Consumer<byte[]> writer) {
            this.writer = writer;
            return this;
        }

        /** Set a custom option negotiator. */
        public Builder negotiator(OptionNegotiator negotiator) {
            this.negotiator = negotiator;
            return this;
        }

        /** Build the gateway. */
        public TelnetGateway build() {
            return new TelnetGateway(this);
        }
    }
}
