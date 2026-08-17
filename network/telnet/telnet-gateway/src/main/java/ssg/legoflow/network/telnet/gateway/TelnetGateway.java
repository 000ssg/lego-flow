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
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * Terminal terminal = VT100Terminal.create(config);
 * TelnetGateway gateway = TelnetGateway.forTerminal(terminal)
 *         .build(socket::read, socket::write);
 *
 * // Feed data from socket
 * gateway.feed(socket.read());
 *
 * // Read terminal output (already escaped for Telnet)
 * byte[] output = gateway.drain();
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
    private final List<GatewayListener> listeners;
    private boolean echoEnabled = true;
    private boolean suppressGoAhead = true;

    @FunctionalInterface
    public interface GatewayListener {
        void onEvent(GatewayEvent event);
    }

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
        DISCONNECTED
    }

    private TelnetGateway(Builder builder) {
        this.terminal = Objects.requireNonNull(builder.terminal, "terminal must not be null");
        this.negotiator = builder.negotiator != null ? builder.negotiator : new OptionNegotiator();
        this.listeners = new CopyOnWriteArrayList<>();

        String termType = terminal.type();
        this.ttypeHandler = TTYPEHandler.localType(termType)
                .onRemoteType(type -> fire(GatewayEvent.TTYPE_EXCHANGED));

        this.nawsHandler = NAWSHandler.localSize(terminal.config().cols(), terminal.config().rows())
                .onRemoteSize((cols, rows) -> fire(GatewayEvent.RESIZED));

        this.connection = TelnetConnection.builder()
                .writer(builder.writer)
                .onData(this::handleData)
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
     */
    public void send(byte[] data) {
        connection.send(data);
    }

    /**
     * Send a string to the peer.
     */
    public void send(String text) {
        connection.send(text);
    }

    /**
     * Feed data to the terminal and send output to the peer.
     */
    public void feedTerminal(byte[] data) {
        terminal.feed(data);
        // Render and send
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
     * Set echo mode.
     */
    public void setEchoEnabled(boolean enabled) {
        this.echoEnabled = enabled;
    }

    // --- Protocol handlers ---

    private void handleData(byte[] data) {
        // Feed data to terminal
        terminal.feed(data);

        // Echo back if enabled
        if (echoEnabled) {
            connection.send(data);
        }
    }

    private void handleNegotiate(TelnetCommand command, int option) {
        TelnetCommand response = negotiator.negotiate(command, option);
        connection.sendNegotiate(response, option);

        // Handle special options
        if (option == TelnetOption.ECHO.code()) {
            if (command == TelnetCommand.DO && response == TelnetCommand.WILL) {
                echoEnabled = true;
            } else if (command == TelnetCommand.DONT && response == TelnetCommand.WONT) {
                echoEnabled = false;
            }
        }
    }

    private void handleSubnegotiation(int option, List<Integer> data) {
        if (option == TelnetOption.TTYPE.code()) {
            byte[] response = ttypeHandler.handle(data);
            if (response != null) {
                connection.sendSubnegotiation(option, response);
            }
        } else if (option == TelnetOption.NAWS.code()) {
            nawsHandler.handle(data);
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
