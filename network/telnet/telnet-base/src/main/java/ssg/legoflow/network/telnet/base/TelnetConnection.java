package ssg.legoflow.network.telnet.base;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A single Telnet session over a byte transport.
 *
 * <p>This class provides a transport-agnostic Telnet protocol implementation
 * (RFC 854). It handles the parser state machine, IAC escaping on output,
 * and exposes a simple callback-based API for application data and protocol events.
 *
 * <p>Usage pattern:
 * <pre>{@code
 * TelnetConnection conn = TelnetConnection.builder()
 *         .onData(data -> System.out.println(new String(data)))
 *         .build(socket::read, socket::write);
 *
 * conn.send("Hello, server!\n");
 * conn.sendCommand(TelnetCommand.AYT);
 * }</pre>
 *
 * @since 0.2.0
 */
public class TelnetConnection {

    private final TelnetParser parser;
    private final Consumer<byte[]> writer;
    private final Consumer<byte[]> dataCallback;
    private final Consumer<TelnetCommand> commandCallback;
    private final NegotiateCallback negotiateCallback;
    private final Consumer<SubnegotiationEvent> subnegotiationCallback;

    /** Callback for negotiation events. */
    @FunctionalInterface
    public interface NegotiateCallback {
        void onNegotiate(TelnetCommand command, int option);
    }

    /** Subnegotiation event. */
    public record SubnegotiationEvent(int option, List<Integer> data) {}

    private TelnetConnection(Builder builder) {
        this.writer = Objects.requireNonNull(builder.writer, "writer must not be null");
        this.dataCallback = builder.dataCallback != null
                ? builder.dataCallback : data -> {};
        this.commandCallback = builder.commandCallback != null
                ? builder.commandCallback : cmd -> {};
        this.negotiateCallback = builder.negotiateCallback != null
                ? builder.negotiateCallback : (cmd, opt) -> {};
        this.subnegotiationCallback = builder.subnegotiationCallback != null
                ? builder.subnegotiationCallback : evt -> {};

        this.parser = new TelnetParser(new TelnetListener() {
            @Override
            public void onData(List<Integer> data) {
                byte[] bytes = toBytes(data);
                dataCallback.accept(bytes);
            }

            @Override
            public void onCommand(TelnetCommand cmd) {
                commandCallback.accept(cmd);
            }

            @Override
            public void onNegotiate(TelnetCommand cmd, int option) {
                negotiateCallback.onNegotiate(cmd, option);
            }

            @Override
            public void onSubnegotiation(int option, List<Integer> data) {
                subnegotiationCallback.accept(new SubnegotiationEvent(option, data));
            }
        });
    }

    /** Create a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Feed bytes received from the remote peer.
     */
    public void feed(byte[] bytes) {
        parser.feed(bytes);
    }

    /**
     * Send application data to the remote peer (IAC auto-escaped).
     */
    public void send(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        byte[] escaped = escapeIac(data);
        writer.accept(escaped);
    }

    /**
     * Send a string (UTF-8 encoded, IAC auto-escaped).
     */
    public void send(String text) {
        Objects.requireNonNull(text, "text must not be null");
        send(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Send a single-byte Telnet command (NOP, DM, BRK, IP, AO, AYT, EC, EL, GA).
     */
    public void sendCommand(TelnetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.hasOption()) {
            throw new IllegalArgumentException("use sendNegotiate() for " + command);
        }
        writer.accept(new byte[]{(byte) IAC, (byte) command.code()});
    }

    /**
     * Send a negotiation command (WILL, WONT, DO, DONT).
     */
    public void sendNegotiate(TelnetCommand command, int option) {
        Objects.requireNonNull(command, "command must not be null");
        if (!command.hasOption()) {
            throw new IllegalArgumentException("use sendCommand() for " + command);
        }
        writer.accept(new byte[]{(byte) IAC, (byte) command.code(), (byte) option});
    }

    /**
     * Send a subnegotiation (SB...SE).
     */
    public void sendSubnegotiation(int option, byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        byte[] escaped = escapeIac(data);
        byte[] msg = new byte[3 + escaped.length + 2]; // IAC SB opt [data] IAC SE
        int i = 0;
        msg[i++] = (byte) IAC;
        msg[i++] = (byte) TelnetCommand.SB.code();
        msg[i++] = (byte) option;
        System.arraycopy(escaped, 0, msg, i, escaped.length);
        i += escaped.length;
        msg[i++] = (byte) IAC;
        msg[i++] = (byte) TelnetCommand.SE.code();
        writer.accept(msg);
    }

    /** Flush buffered data. */
    public void flush() {
        parser.flush();
    }

    /** Current parser state. */
    public ParserState parserState() {
        return parser.state();
    }

    /** Reset the parser. */
    public void reset() {
        parser.reset();
    }

    private static final int IAC = 255;

    /**
     * Escape IAC bytes by doubling them (RFC 854).
     */
    static byte[] escapeIac(byte[] data) {
        int escapedCount = 0;
        for (byte b : data) {
            if (b == (byte) IAC) escapedCount++;
        }
        if (escapedCount == 0) return data.clone();

        byte[] result = new byte[data.length + escapedCount];
        int pos = 0;
        for (byte b : data) {
            if (b == (byte) IAC) {
                result[pos++] = (byte) IAC;
                result[pos++] = (byte) IAC;
            } else {
                result[pos++] = b;
            }
        }
        return result;
    }

    private static byte[] toBytes(List<Integer> ints) {
        byte[] result = new byte[ints.size()];
        for (int i = 0; i < ints.size(); i++) {
            result[i] = ints.get(i).byteValue();
        }
        return result;
    }

    // --- Builder ---

    /** Builder for {@link TelnetConnection}. */
    public static class Builder {

        private Consumer<byte[]> writer;
        private Consumer<byte[]> dataCallback;
        private Consumer<TelnetCommand> commandCallback;
        private NegotiateCallback negotiateCallback;
        private Consumer<SubnegotiationEvent> subnegotiationCallback;

        /** Set the writer for sending bytes to the peer. */
        public Builder writer(Consumer<byte[]> writer) {
            this.writer = writer;
            return this;
        }

        /** Set the callback for received application data. */
        public Builder onData(Consumer<byte[]> callback) {
            this.dataCallback = callback;
            return this;
        }

        /** Set the callback for protocol commands. */
        public Builder onCommand(Consumer<TelnetCommand> callback) {
            this.commandCallback = callback;
            return this;
        }

        /** Set the callback for negotiation events. */
        public Builder onNegotiate(NegotiateCallback callback) {
            this.negotiateCallback = callback;
            return this;
        }

        /** Set the callback for subnegotiations. */
        public Builder onSubnegotiation(Consumer<SubnegotiationEvent> callback) {
            this.subnegotiationCallback = callback;
            return this;
        }

        /** Build the connection. */
        public TelnetConnection build() {
            return new TelnetConnection(this);
        }
    }
}
