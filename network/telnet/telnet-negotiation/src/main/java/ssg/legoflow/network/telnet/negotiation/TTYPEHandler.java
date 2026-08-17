package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetCommand;
import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Handles Terminal Type (TTYPE, option 24) negotiation (RFC 1091).
 *
 * <p>Supports both sending and receiving terminal type information.
 * The IS suboption sends the local terminal type; the SEND suboption
 * requests the peer's terminal type.
 *
 * <p>Usage:
 * <pre>{@code
 * TTYPEHandler handler = TTYPEHandler.localType("xterm")
 *         .onRemoteType(type -> System.out.println("Remote type: " + type));
 *
 * // When we receive IAC SB 24 1 IAC SE (SEND request), the handler
 * // automatically responds with IAC SB 24 0 "xterm\0" IAC SE (IS).
 * }</pre>
 *
 * @since 0.2.0
 */
public class TTYPEHandler {

    /** IS suboption — sends terminal type. */
    public static final int IS = 0;

    /** SEND suboption — requests terminal type. */
    public static final int SEND = 1;

    private final Supplier<String> localType;
    private final RemoteTypeCallback remoteTypeCallback;

    @FunctionalInterface
    public interface RemoteTypeCallback {
        void onRemoteType(String type);
    }

    private TTYPEHandler(Supplier<String> localType, RemoteTypeCallback remoteTypeCallback) {
        this.localType = Objects.requireNonNull(localType, "localType must not be null");
        this.remoteTypeCallback = remoteTypeCallback != null
                ? remoteTypeCallback : type -> {};
    }

    /**
     * Create a handler with a fixed local terminal type.
     *
     * @param type the terminal type string (e.g. "xterm", "vt100")
     */
    public static TTYPEHandler localType(String type) {
        return new TTYPEHandler(() -> type, null);
    }

    /**
     * Create a handler with a dynamic local terminal type.
     *
     * @param typeSupplier supplies the terminal type string
     */
    public static TTYPEHandler localType(Supplier<String> typeSupplier) {
        return new TTYPEHandler(typeSupplier, null);
    }

    /**
     * Set the callback for receiving the remote terminal type.
     */
    public TTYPEHandler onRemoteType(RemoteTypeCallback callback) {
        return new TTYPEHandler(localType, callback);
    }

    /**
     * Handle a received TTYPE subnegotiation.
     *
     * @param data the subnegotiation payload (first byte is IS or SEND)
     * @return bytes to send back, or null if no response needed
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int suboption = data.get(0);
        return switch (suboption) {
            case SEND -> {
                // Peer wants our type; respond with IS
                String type = localType.get();
                byte[] payload = type.getBytes(StandardCharsets.US_ASCII);
                byte[] response = new byte[payload.length + 2];
                response[0] = (byte) IS;
                System.arraycopy(payload, 0, response, 1, payload.length);
                response[response.length - 1] = 0; // null terminator
                yield response;
            }
            case IS -> {
                // Peer sent their type
                if (data.size() > 1) {
                    byte[] typeBytes = new byte[data.size() - 1];
                    for (int i = 1; i < data.size(); i++) {
                        typeBytes[i - 1] = data.get(i).byteValue();
                    }
                    // Trim null terminator
                    int nullIdx = -1;
                    for (int i = 0; i < typeBytes.length; i++) {
                        if (typeBytes[i] == 0) { nullIdx = i; break; }
                    }
                    String type = nullIdx >= 0
                            ? new String(typeBytes, 0, nullIdx, StandardCharsets.US_ASCII)
                            : new String(typeBytes, StandardCharsets.US_ASCII);
                    remoteTypeCallback.onRemoteType(type);
                }
                yield null; // No response to IS
            }
            default -> null;
        };
    }

    /**
     * Send a SEND request to the peer (ask for their terminal type).
     */
    public void sendRequest(TelnetConnection conn) {
        conn.sendSubnegotiation(TelnetOption.TTYPE.code(), new byte[]{(byte) SEND});
    }

    /**
     * Send our terminal type to the peer.
     */
    public void sendType(TelnetConnection conn) {
        String type = localType.get();
        byte[] payload = type.getBytes(StandardCharsets.US_ASCII);
        byte[] msg = new byte[payload.length + 2];
        msg[0] = (byte) IS;
        System.arraycopy(payload, 0, msg, 1, payload.length);
        msg[msg.length - 1] = 0;
        conn.sendSubnegotiation(TelnetOption.TTYPE.code(), msg);
    }
}
