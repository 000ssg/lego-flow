package ssg.legoflow.network.telnet.negotiation;

import ssg.legoflow.network.telnet.base.TelnetOption;

import java.util.List;

/**
 * Handler for LINEMODE option subnegotiation (RFC 1143).
 *
 * <p>This is a stub implementation. Full LINEMODE support requires
 * a line buffer, edit mode negotiation, and send mode tracking.
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Only sends LINEMODE IS (mode 0) with default settings</li>
 *   <li>No line editing support</li>
 *   <li>No send mode negotiation</li>
 *   <li>State is not tracked</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class LinemodeHandler {

    /** LINEMODE IS — send current mode to peer. */
    private static final int LINEMODE_IS = 0;
    /** LINEMODE SEND — peer requests current mode. */
    private static final int LINEMODE_SEND = 1;
    /** LINEMODE START — start linemode. */
    private static final int LINEMODE_START = 2;
    /** LINEMODE OFF — stop linemode. */
    private static final int LINEMODE_OFF = 3;
    /** LINEMODE DEFAULT — reset to default mode. */
    private static final int LINEMODE_DEFAULT = 4;

    private boolean active;

    private LinemodeHandler() {
        this.active = false;
    }

    /** Create a new LinemodeHandler. */
    public static LinemodeHandler create() {
        return new LinemodeHandler();
    }

    /**
     * Handle LINEMODE subnegotiation data.
     *
     * @param data the subnegotiation bytes
     * @return response bytes to send back, or null if no response needed
     */
    public byte[] handle(List<Integer> data) {
        if (data.isEmpty()) return null;

        int command = data.get(0);
        return switch (command) {
            case LINEMODE_SEND -> {
                // Peer requests our current mode
                // Response: IAC SB LINEMODE IS <mode> <send-mode> IAC SE
                yield new byte[]{LINEMODE_IS, 0};
            }
            case LINEMODE_IS -> {
                // Peer sends their mode — no response needed
                yield null;
            }
            case LINEMODE_START -> {
                active = true;
                yield null;
            }
            case LINEMODE_OFF -> {
                active = false;
                yield null;
            }
            case LINEMODE_DEFAULT -> {
                active = false;
                yield new byte[]{LINEMODE_IS, 0};
            }
            default -> null;
        };
    }

    /** Check if linemode is currently active. */
    public boolean isActive() {
        return active;
    }
}
