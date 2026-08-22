package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetConnection;
import ssg.legoflow.network.telnet.base.TelnetOption;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
/**
 * Handles TERMINAL-SPEED (option 42) negotiation (RFC 1079).
 *
 * <p>The subnegotiation carries a null-terminated ASCII string
 * representing the terminal baud rate (e.g. "38400").
 *
 * @since 0.2.0
 */
public class SpeedHandler {

    /** IS suboption — sends speed. */
    public static final int IS = 0;

    /** SEND suboption — requests speed. */
    public static final int SEND = 1;

    private final Supplier<String> localSpeed;
    private final RemoteSpeedCallback remoteSpeedCallback;

    @FunctionalInterface
    public interface RemoteSpeedCallback {
        void onRemoteSpeed(String speed);
    }

    private SpeedHandler(Supplier<String> localSpeed, RemoteSpeedCallback remoteSpeedCallback) {
        this.localSpeed = Objects.requireNonNull(localSpeed, "localSpeed must not be null");
        this.remoteSpeedCallback = remoteSpeedCallback != null
                ? remoteSpeedCallback : speed -> {};
    }

    /**
     * Create a handler with a fixed local speed.
     */
    public static SpeedHandler localSpeed(String speed) {
        return new SpeedHandler(() -> speed, null);
    }

    /**
     * Set the callback for receiving remote speed.
     */
    public SpeedHandler onRemoteSpeed(RemoteSpeedCallback callback) {
        return new SpeedHandler(localSpeed, callback);
    }

    /**
     * Handle a received TERMINAL-SPEED subnegotiation.
     *
     * @param data the subnegotiation payload
     * @return bytes to send back, or null
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int suboption = data.get(0);
        return switch (suboption) {
            case SEND -> {
                String speed = localSpeed.get();
                byte[] payload = speed.getBytes(StandardCharsets.US_ASCII);
                byte[] response = new byte[payload.length + 2];
                response[0] = (byte) IS;
                System.arraycopy(payload, 0, response, 1, payload.length);
                response[response.length - 1] = 0;
                yield response;
            }
            case IS -> {
                if (data.size() > 1) {
                    byte[] speedBytes = new byte[data.size() - 1];
                    for (int i = 1; i < data.size(); i++) {
                        speedBytes[i - 1] = data.get(i).byteValue();
                    }
                    int nullIdx = -1;
                    for (int i = 0; i < speedBytes.length; i++) {
                        if (speedBytes[i] == 0) { nullIdx = i; break; }
                    }
                    String speed = nullIdx >= 0
                            ? new String(speedBytes, 0, nullIdx, StandardCharsets.US_ASCII)
                            : new String(speedBytes, StandardCharsets.US_ASCII);
                    remoteSpeedCallback.onRemoteSpeed(speed);
                }
                yield null;
            }
            default -> null;
        };
    }

    /**
     * Send a SEND request.
     */
    public void sendRequest(TelnetConnection conn) {
        conn.sendSubnegotiation(TelnetOption.TERMINAL_SPEED.code(),
                new byte[]{(byte) SEND});
    }

    /**
     * Send our speed to the peer.
     */
    public void sendSpeed(TelnetConnection conn) {
        String speed = localSpeed.get();
        byte[] payload = speed.getBytes(StandardCharsets.US_ASCII);
        byte[] msg = new byte[payload.length + 2];
        msg[0] = (byte) IS;
        System.arraycopy(payload, 0, msg, 1, payload.length);
        msg[msg.length - 1] = 0;
        conn.sendSubnegotiation(TelnetOption.TERMINAL_SPEED.code(), msg);
    }
}
