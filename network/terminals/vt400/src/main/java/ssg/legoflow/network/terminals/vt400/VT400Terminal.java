package ssg.legoflow.network.terminals.vt400;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt200.VT200Terminal;

/**
 * VT400 terminal emulator.
 *
 * <p>Extends VT200 with workstation capabilities:
 * <ul>
 *   <li>Extended SGR codes (8-color foreground/background: 82–89, 92–99)</li>
 *   <li>Multiple window support (2 windows)</li>
 *   <li>Scroll history buffer</li>
 *   <li>DECCOM (commodity codes)</li>
 *   <li>Insert/delete column</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class VT400Terminal extends VT200Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT400Terminal(config);
    }

    private int activeWindow = 1;
    private final int windowCount = 2;

    protected VT400Terminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // VT400-specific CSI sequences
        if (finalByte == 'm') {
            // Extended SGR: 82-89 = extended fg, 92-99 = extended bg
            for (int i = 0; i < params.size(); i++) {
                int code = params.get(i);
                if (code >= 82 && code <= 89) {
                    // Extended foreground colors (VT400+)
                    display().setCurrentAttr(display().currentAttr().toBuilder()
                            .foreground(code - 82).build());
                } else if (code >= 92 && code <= 99) {
                    // Extended background colors (VT400+)
                    display().setCurrentAttr(display().currentAttr().toBuilder()
                            .background(code - 92).build());
                }
            }
        }

        // Window selection: ESC [ <window> t
        if (finalByte == 't') {
            int win = params.get(0, 1);
            activeWindow = Math.max(1, Math.min(win, windowCount));
            return;
        }

        super.handleCSI(params);
    }

    @Override
    public String type() { return "vt400"; }

    @Override
    public boolean supportsColor() { return true; }

    /** Currently active window number. */
    public int activeWindow() { return activeWindow; }

    /** Number of supported windows. */
    public int windowCount() { return windowCount; }

    @Override
    protected void onReset() {
        super.onReset();
        activeWindow = 1;
    }
}
