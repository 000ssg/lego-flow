package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Handles NAWS (Negotiate About Window Size, option 31) (RFC 1073).
 *
 * <p>The NAWS subnegotiation carries the terminal window dimensions
 * as two 16-bit big-endian values: columns and rows.
 *
 * <p>Usage:
 * <pre>{@code
 * NAWSHandler handler = NAWSHandler.localSize(80, 24)
 *         .onRemoteSize((cols, rows) -> updateTerminal(cols, rows));
 *
 * // When we receive IAC SB 31 colsHi colsLo rowsHi rowsLo IAC SE,
 * // the handler parses and fires the callback.
 * }</pre>
 *
 * @since 0.2.0
 */
public class NAWSHandler {

    private final Supplier<WindowSize> localSize;
    private final RemoteSizeCallback remoteSizeCallback;

    @FunctionalInterface
    public interface RemoteSizeCallback {
        void onRemoteSize(int cols, int rows);
    }

    /** Window dimensions. */
    public record WindowSize(int cols, int rows) {
        public WindowSize {
            if (cols < 1 || cols > 65535)
                throw new IllegalArgumentException("cols must be 1-65535");
            if (rows < 1 || rows > 65535)
                throw new IllegalArgumentException("rows must be 1-65535");
        }
    }

    private NAWSHandler(Supplier<WindowSize> localSize, RemoteSizeCallback remoteSizeCallback) {
        this.localSize = Objects.requireNonNull(localSize, "localSize must not be null");
        this.remoteSizeCallback = remoteSizeCallback != null
                ? remoteSizeCallback : (c, r) -> {};
    }

    /**
     * Create a handler with fixed local window size.
     */
    public static NAWSHandler localSize(int cols, int rows) {
        return new NAWSHandler(() -> new WindowSize(cols, rows), null);
    }

    /**
     * Create a handler with dynamic local window size.
     */
    public static NAWSHandler localSize(Supplier<WindowSize> sizeSupplier) {
        return new NAWSHandler(sizeSupplier, null);
    }

    /**
     * Set the callback for receiving remote window size.
     */
    public NAWSHandler onRemoteSize(RemoteSizeCallback callback) {
        return new NAWSHandler(localSize, callback);
    }

    /**
     * Handle a received NAWS subnegotiation.
     *
     * <p>Expected format: colsHi colsLo rowsHi rowsLo (4 bytes).
     *
     * @param data the subnegotiation payload
     */
    public void handle(List<Integer> data) {
        if (data.size() >= 4) {
            int cols = (data.get(0) & 0xFF) << 8 | (data.get(1) & 0xFF);
            int rows = (data.get(2) & 0xFF) << 8 | (data.get(3) & 0xFF);
            if (cols >= 1 && rows >= 1) {
                remoteSizeCallback.onRemoteSize(cols, rows);
            }
        }
    }

    /**
     * Send our window size to the peer.
     */
    public void sendSize(TelnetConnection conn) {
        WindowSize size = localSize.get();
        byte[] data = new byte[4];
        data[0] = (byte) (size.cols() >> 8);
        data[1] = (byte) (size.cols() & 0xFF);
        data[2] = (byte) (size.rows() >> 8);
        data[3] = (byte) (size.rows() & 0xFF);
        conn.sendSubnegotiation(TelnetOption.NAWS.code(), data);
    }
}
