package ssg.legoflow.network.telnet.gateway;

import ssg.legoflow.network.telnet.base.*;
import ssg.legoflow.network.telnet.negotiation.*;
import ssg.legoflow.network.terminals.base.io.Terminal;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Bridges a Telnet connection to a terminal emulator.
 *
 * <p>The gateway sits between the network transport and the terminal:
 * <ul>
 *   <li><b>Inbound (peer → terminal)</b>: Parses Telnet protocol from the peer,
 *       strips IAC commands, and feeds clean application data to the terminal.</li>
 *   <li><b>Outbound (terminal → peer)</b>: Renders terminal output and sends it
 *       back over the Telnet connection with IAC escaping.</li>
 * </ul>
 *
 * <p>Option negotiation is handled automatically:
 * <ul>
 *   <li>ECHO — enabled by default (server echoes input)</li>
 *   <li>SUPPRESS_GO_AHEAD — enabled by default</li>
 *   <li>TTYPE — responds with the terminal type</li>
 *   <li>NAWS — updates terminal dimensions on resize</li>
 *   <li>BINARY — enables 8-bit binary transmission (RFC 856)</li>
 *   <li>LINEMODE — sends default LINEMODE IS response (RFC 1143)</li>
 *   <li>NEW_ENV — provides TERM/COLS/LINES environment (RFC 1408)</li>
 * </ul>
 *
 * <p>Single-byte commands (BRK, DM, GA, EC, EL, AYT, IP, NOP) are dispatched
 * to {@link GatewayListener} callbacks for application-level handling.
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

    @FunctionalInterface
    public interface GatewayListener {
        void onEvent(GatewayEvent event);
    }

    /** Single-byte Telnet command received from peer. */
    public record CommandEvent(TelnetCommand command) {}

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
        ENV_EXCHANGED
    }

    private TelnetGateway(Builder builder) {
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must not be null");
        this.negotiator = builder.negotiator != null ? builder.negotiator : new GatewayNegotiator();
        this.listeners = new CopyOnWriteArrayList<>();

        String termType = terminal.type();
        this.ttypeHandler = TTYPEHandler.localType(termType)
                .onRemoteType(type -> fire(GatewayEvent.TTYPE_EXCHANGED));

        this.nawsHandler = NAWSHandler.localSize(terminal.config().cols(), terminal.config().rows())
                .onRemoteSize((cols, rows) -> fire(GatewayEvent.RESIZED));

        this.speedHandler = SpeedHandler.localSpeed("38400");

        this.binaryHandler = BinaryHandler.create();

        this.linemodeHandler = LinemodeHandler.create();

        this.newEnvHandler = NewEnvHandler.create(termType, terminal.config().cols(), terminal.config().rows());

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
     */
    public void feed(byte[] bytes) {
        connection.feed(bytes);
        connection.flush();
    }

    /**
     * Send data to the peer (from the terminal or application).
     * IAC bytes are automatically escaped per RFC 854.
     */
    public void send(byte[] data) {
        connection.send(data);
    }

    /**
     * Send a string to the peer (UTF-8 encoded, IAC auto-escaped).
     */
    public void send(String text) {
        connection.send(text);
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

    /**
     * Get the underlying terminal.
     */
    public Terminal terminal() {
        return terminal;
    }

    /**
     * Get the underlying TelnetConnection.
     */
    public TelnetConnection connection() {
        return connection;
    }

    /**
     * Get the option negotiator.
     */
    public OptionNegotiator negotiator() {
        return negotiator;
    }

    /**
     * Check if echo is enabled.
     */
    public boolean isEchoEnabled() {
        return echoEnabled;
    }

    /**
     * Enable or disable echo.
     */
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }

    /**
     * Check if SUPPRESS GO AHEAD is enabled.
     */
    public boolean isSuppressGoAhead() {
        return suppressGoAhead;
    }

    /**
     * Get the binary mode handler.
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

    /**
     * Get an environment variable from NEW_ENV.
     *
     * @param name the variable name
     * @return the value, or null if not set
     */
    public String getEnv(String name) {
        return newEnvHandler.get(name);
    }

    /**
     * Set an environment variable for NEW_ENV.
     *
     * @param name  the variable name
     * @param value the value
     */
    public void setEnv(String name, String value) {
        newEnvHandler.set(name, value);
    }

    /**
     * Register a gateway event listener.
     */
    public void addListener(GatewayListener listener) {
        Objects.requireNonNull(listener);
        listeners.add(listener);
    }

    /**
     * Remove a gateway event listener.
     */
    public void removeListener(GatewayListener listener) {
        listeners.remove(listener);
    }

    // --- Protocol handlers ---

    /** Handle application data from peer. */
    private void handleData(byte[] data) {
        terminal.feed(data);

        // Echo back if enabled
        if (echoEnabled) {
            connection.send(data);
        }
    }

    /** Handle single-byte Telnet commands (BRK, DM, GA, EC, EL, AYT, IP, NOP). */
    private void handleCommand(TelnetCommand command) {
        // Fire event for application-level handling
        fire(GatewayEvent.COMMAND);

        switch (command) {
            case NOP -> {/* No Operation — silently accepted */}
            case DM -> {/* Data Mark — flush output, sync point */}
            case GA -> {/* Go Ahead — no-op when SUPPRESS_GO_AHEAD is active */}
            case BRK -> {/* Break — signal to application */}
            case IP -> {/* Interrupt Process — typically generates SIGINT */}
            case AYT -> {/* Are You There — response is handled by application */}
            case EC -> {/* Erase Character — handled by terminal */}
            case EL -> {/* Erase Line — handled by terminal */}
            case SB -> {/* Start Subnegotiation — handled by parser */}
            case SE -> {/* End Subnegotiation — handled by parser */}
            default -> {
                // Negotiation commands (WILL/WONT/DO/DONT) are handled separately
                // by onNegotiate callback, not here.
            }
        }
    }

    /** Handle option negotiation (WILL/WONT/DO/DONT). */
    private void handleNegotiate(TelnetCommand command, int option) {
        TelnetCommand response = negotiator.negotiate(command, option);
        connection.sendNegotiate(response, option);

        // Handle specific options
        TelnetOption opt = TelnetOption.fromCode(option);
        if (opt == null) return; // Unknown option — already handled by negotiator

        switch (opt) {
            case ECHO -> {
                // Echo enabled when we respond WILL (to DO) or keep enabled (WILL to DONT)
                echoEnabled = response == TelnetCommand.WILL;
            }
            case SUPPRESS_GO_AHEAD -> {
                // Suppress GO AHEAD enabled when we respond WILL
                suppressGoAhead = response == TelnetCommand.WILL;
            }
            case BINARY -> {
                // RFC 856: DO BINARY means peer wants us to send binary
                //           WILL BINARY means peer will send binary
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
            default -> {/* Other options handled by negotiator */}
        }
    }

    /** Handle subnegotiation data. */
    private void handleSubnegotiation(int option, List<Integer> data) {
        TelnetOption opt = TelnetOption.fromCode(option);
        if (opt == null) return; // Unknown subnegotiation

        switch (opt) {
            case TTYPE -> {
                byte[] ttypeResponse = ttypeHandler.handle(data);
                if (ttypeResponse != null) {
                    connection.sendSubnegotiation(option, ttypeResponse);
                }
            }
            case NAWS -> {
                nawsHandler.handle(data);
            }
            case TERMINAL_SPEED -> {
                byte[] speedResponse = speedHandler.handle(data);
                if (speedResponse != null) {
                    connection.sendSubnegotiation(option, speedResponse);
                }
            }
            case LINEMODE -> {
                byte[] linemodeResponse = linemodeHandler.handle(data);
                if (linemodeResponse != null) {
                    connection.sendSubnegotiation(option, linemodeResponse);
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
                if (!data.isEmpty()) {
                    // BINARY data subnegotiation — ignore (rare in practice)
                }
            }
            default -> {/* Unknown subnegotiation — ignored */}
        }
    }

    private void fire(GatewayEvent event) {
        for (GatewayListener listener : listeners) {
            listener.onEvent(event);
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
