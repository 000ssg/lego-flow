package ssg.legoflow.network.terminals.vt200;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;

import java.util.Objects;

/**
 * VT200 terminal emulator.
 *
 * <p>Extends VT100 with mechanical terminal capabilities:
 * <ul>
 *   <li>Function keys (PF1–PF3, PL1–PL6)</li>
 *   <li>Video reverse mode</li>
 *   <li>Line feed variant handling</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class VT200Terminal extends VT100Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT200Terminal(config);
    }

    private boolean videoReverse;

    protected VT200Terminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // VT200 adds SGR code 52 (video reverse) and 55 (video normal)
        if (finalByte == 'm') {
            for (int i = 0; i < params.size(); i++) {
                int code = params.get(i);
                if (code == 52) {
                    videoReverse = true;
                } else if (code == 55) {
                    videoReverse = false;
                }
            }
        }

        super.handleCSI(params);
    }

    @Override
    public String type() { return "vt200"; }

    @Override
    public boolean supportsColor() { return true; }

    /** Check if video reverse is active. */
    public boolean isVideoReverse() { return videoReverse; }

    @Override
    protected void onReset() {
        super.onReset();
        videoReverse = false;
    }
}
