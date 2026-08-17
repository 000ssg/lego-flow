package ssg.legoflow.network.terminals.ansi;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;

/**
 * ANSI X3.64 standard terminal emulator.
 *
 * <p>Implements the ANSI standard subset of terminal control sequences,
 * excluding DEC private mode extensions (those starting with ESC [ ?).
 *
 * <p>Supported sequences:
 * <ul>
 *   <li>CSI cursor motion (A, B, C, D, E, F, G, H, f, d)</li>
 *   <li>SGR attributes (0-7, 30-37, 40-47)</li>
 *   <li>Erase (J, K, X)</li>
 *   <li>Insert/delete (L, M, P, @)</li>
 *   <li>Device control (DC1-DC4)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class ANSITerminal extends VT100Terminal {

    public static Terminal create(TerminalConfig config) {
        return new ANSITerminal(config);
    }

    protected ANSITerminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        // ANSI terminal ignores DEC private modes
        if (params.intermediates().equals("?")) {
            return; // silently ignore DEC private sequences
        }
        super.handleCSI(params);
    }

    @Override
    public String type() { return "ansi"; }

    @Override
    public boolean supportsColor() { return true; }
}
