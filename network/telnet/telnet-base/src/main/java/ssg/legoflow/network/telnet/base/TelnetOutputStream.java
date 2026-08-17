package ssg.legoflow.network.telnet.base;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * An output stream that automatically escapes IAC bytes (RFC 854).
 *
 * <p>Wraps a target {@link OutputStream} and doubles any IAC (255) bytes
 * before writing them. Also provides convenience methods for sending
 * Telnet protocol commands.
 *
 * @since 0.2.0
 */
public class TelnetOutputStream extends OutputStream {

    private static final int IAC = 0xFF;

    private final OutputStream target;

    /**
     * Wrap an output stream with IAC escaping.
     *
     * @param target the underlying output stream
     */
    public TelnetOutputStream(OutputStream target) {
        this.target = Objects.requireNonNull(target, "target must not be null");
    }

    @Override
    public void write(int b) throws IOException {
        if ((b & 0xFF) == IAC) {
            target.write(IAC);
            target.write(IAC);
        } else {
            target.write(b);
        }
    }

    @Override
    public void write(byte[] data) throws IOException {
        write(data, 0, data.length);
    }

    @Override
    public void write(byte[] data, int off, int len) throws IOException {
        for (int i = off; i < off + len; i++) {
            write(data[i]);
        }
    }

    /**
     * Send a single-byte Telnet command.
     */
    public void sendCommand(TelnetCommand cmd) throws IOException {
        target.write(IAC);
        target.write(cmd.code());
        target.flush();
    }

    /**
     * Send a negotiation command (WILL/WONT/DO/DONT).
     */
    public void sendNegotiate(TelnetCommand cmd, int option) throws IOException {
        target.write(IAC);
        target.write(cmd.code());
        target.write(option);
        target.flush();
    }

    /**
     * Send a subnegotiation (SB...SE).
     */
    public void sendSubnegotiation(int option, byte[] data) throws IOException {
        target.write(IAC);
        target.write(TelnetCommand.SB.code());
        target.write(option);
        for (byte b : data) {
            if ((b & 0xFF) == IAC) {
                target.write(IAC);
                target.write(IAC);
            } else {
                target.write(b);
            }
        }
        target.write(IAC);
        target.write(TelnetCommand.SE.code());
        target.flush();
    }

    @Override
    public void flush() throws IOException {
        target.flush();
    }

    @Override
    public void close() throws IOException {
        target.close();
    }
}
