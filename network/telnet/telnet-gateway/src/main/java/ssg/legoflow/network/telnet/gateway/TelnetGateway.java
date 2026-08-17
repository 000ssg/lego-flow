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
 * <p>Usage:
 * <pre>{@code
 * Terminal terminal = VT100Terminal.create(config);
 * TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
 *         .writer(socket::write)
 *         .build();
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

    @FunctionalInterface
    public interface GatewayListener {
        void onEvent(GatewayEvent event);
    }

    /** Single-byte Telnet command received from peer. */
    public record CommandEvent(TelnetCommand command) {}

    /** Data Mark received or echoed. */
    public record DmEvent(boolean isSync) {}

    /** Line submitted via LINEMODE. */
    public record LineEvent(String line) {}

    /** Remote environment variable received. */
    public record EnvVarEvent(String name, NewEnvHandler.EnvVar variable) {}

    public enum GatewayEvent {
        /** Connection established. */
        CONNECTED,
        /** Option negotiation completed. */
        NEGOTIATED,
        /** Terminal resized. */
        RESIZED,
        /** Terminal type exchanged. */
        TTYPE_EXCHANGED,
        /** Connection closed. */
        DISCONNECTED,
        /** Single-byte command received. */
        COMMAND,
        /** Binary mode negotiated. */
        BINARY_NEGOTIATED,
        /** Environment exchanged. */
        ENV_EXCHANGED,
        /** Data Mark received. */
        DM_RECEIVED,
        /** Data Mark sync complete (sent DM and received echo). */
        DM_SYNC,
        /** LINEMODE activated. */
        LINEMODE_ACTIVE,
        /** LINEMODE deactivated. */
        LINEMODE_INACTIVE,
        /** Line submitted via LINEMODE. */
        LINE_SUBMITTED
    }

    private TelnetGateway(Builder builder) {
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must not be null");
        this.negotiator = builder.negotiator != null ? builder.negotiator : new GatewayNegotiator();
        this.listeners = new CopyOnWriteArrayList<>();
        this.awaitingDmSync = false;

        String termType = terminal.type();
        this.ttypeHandler = TTYPEHandler.localType(termType)
                .onRemoteType(type -> fire(GatewayEvent.TTYPE_EXCHANGED));

        this.nawsHandler = NAWSHandler.localSize(terminal.config().cols(), terminal.config().rows())
                .onRemoteSize((cols, rows) -> fire(GatewayEvent.RESIZED));

        this.speedHandler = SpeedHandler.localSpeed("38400");

        this.binaryHandler = BinaryHandler.create();

        this.linemodeHandler = LinemodeHandler.create()
                .onLineSubmitted(line -> {
                    fire(GatewayEvent.LINE_SUBMITTED);
                    // Submit the line to the terminal as typed input followed by CR
                    terminal.feed((line + "\r").getBytes());
                    List<String> rendered = terminal.render();
                    if (!rendered.isEmpty()) {
                        String output = String.join("\r\n", rendered);
                        send(output);
                    }
                });

        this.newEnvHandler = NewEnvHandler.create(termType, terminal.config().cols(), terminal.config().rows())
                .onRemoteVar((name, variable) -> fire(GatewayEvent.ENV_EXCHANGED));

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
        return new Builder(terminal);
    }

    /**
     * Feed bytes received from the peer into the gateway.
     * Applies binary translation (RFC 856) and IAC stripping before
     * feeding clean data to the terminal.
     */
    public void feed(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return;
        connection.feed(bytes);
        connection.flush();
    }

    /**
     * Send data to the peer (from the terminal or application).
     * Applies byte translation (RFC 856) and IAC escaping.
     */
    public void send(byte[] data) {
        if (data == null || data.length == 0) return;

        byte[] translated = binaryHandler.translateOutbound(data);
        connection.send(translated);
    }

    /**
     * Send a string to the peer (UTF-8 encoded, translated, IAC auto-escaped).
     */
    public void send(String text) {
        Objects.requireNonNull(text, "text must not be null");
        send(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Send a Data Mark for synchronization (RFC 854).
     * The peer should echo the DM back; DM_SYNC event fires when received.
     */
    public void sendDm() {
        awaitingDmSync = true;
        connection.sendCommand(TelnetCommand.DM);
    }

    /**
     * Feed data to the terminal and send rendered output to the peer.
     */
    public void feedTerminal(byte[] data) {
        terminal.feed(data);
        String output = String.join("\r\n", terminal.render());
        if (!output.isEmpty()) {
            send(output);
        }
    }

    /** Get the underlying terminal. */
    public Terminal terminal() {
        return terminal;
    }

    /** Get the underlying TelnetConnection. */
    public TelnetConnection connection() {
        return connection;
    }

    /** Get the option negotiator. */
    public OptionNegotiator negotiator() {
        return negotiator;
    }

    /** Get the binary handler. */
    public BinaryHandler binaryHandler() {
        return binaryHandler;
    }

    /** Get the linemode handler. */
    public LinemodeHandler linemodeHandler() {
        return linemodeHandler;
    }

    /** Get the NEW_ENV handler. */
    public NewEnvHandler newEnvHandler() {
        return newEnvHandler;
    }

    /** Check if echo is enabled. */
    public boolean isEchoEnabled() {
        return echoEnabled;
    }

    /** Enable or disable echo. */
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }

    /** Check if SUPPRESS GO AHEAD is enabled. */
    public boolean isSuppressGoAhead() {
        return suppressGoAhead;
    }

    /** Get the remote environment. */
    public Map<String, NewEnvHandler.EnvVar> remoteEnvironment() {
        return newEnvHandler.getRemoteEnvironment();
    }

    /**
     * Get an environment variable value by name (convenience API).
     *
     * @param name the variable name
     * @return the value, or null if not found
     */
    public String getEnv(String name) {
        return newEnvHandler.get(name);
    }

    /**
     * Set an environment variable (convenience API).
     *
     * @param name  the variable name
     * @param value the value (stored as STRING type)
     */
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
        listeners.add(listener);
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
                fire(GatewayEvent.DM_RECEIVED);
                fire(GatewayEvent.COMMAND);

                if (awaitingDmSync) {
                    awaitingDmSync = false;
                    fire(GatewayEvent.DM_SYNC);
                }
            }
            case BRK -> fire(GatewayEvent.COMMAND);
            case GA -> fire(GatewayEvent.COMMAND);
            case EC -> fire(GatewayEvent.COMMAND);
            case EL -> fire(GatewayEvent.COMMAND);
            case AYT -> fire(GatewayEvent.COMMAND);
            case IP -> fire(GatewayEvent.COMMAND);
            case NOP -> fire(GatewayEvent.COMMAND);
            case AO -> fire(GatewayEvent.COMMAND);
            case SE -> fire(GatewayEvent.COMMAND);
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
                    fire(GatewayEvent.BINARY_NEGOTIATED);
                } else if (command == TelnetCommand.DONT && response == TelnetCommand.WONT) {
                    binaryHandler.setLocalBinary(false);
                } else if (command == TelnetCommand.WILL && response == TelnetCommand.DO) {
                    binaryHandler.setRemoteBinary(true);
                    fire(GatewayEvent.BINARY_NEGOTIATED);
                } else if (command == TelnetCommand.WONT && response == TelnetCommand.DONT) {
                    binaryHandler.setRemoteBinary(false);
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
                    fire(GatewayEvent.LINEMODE_ACTIVE);
                } else {
                    fire(GatewayEvent.LINEMODE_INACTIVE);
                }
            }
            case NEW_ENV -> {
                byte[] envResponse = newEnvHandler.handle(data);
                if (envResponse != null) {
                    connection.sendSubnegotiation(option, envResponse);
                }
                fire(GatewayEvent.ENV_EXCHANGED);
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
