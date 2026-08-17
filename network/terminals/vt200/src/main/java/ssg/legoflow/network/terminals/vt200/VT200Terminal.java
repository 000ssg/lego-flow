package ssg.legoflow.network.terminals.vt200;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import ssg.legoflow.network.terminals.vt100.VT100Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VT200 terminal emulator.
 *
 * <p>Extends VT100 with mechanical terminal capabilities:
 * <ul>
 *   <li>Function keys (PF1–PF3, PL1–PL6)</li>
 *   <li>Video reverse mode (SGR 52/55)</li>
 *   <li>Line feed variant handling</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class VT200Terminal extends VT100Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT200Terminal(config);
    }

    /** Video reverse state (SGR 52/55). */
    protected boolean videoReverse;

    
    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("vt200", config -> VT200Terminal.create(config));
    }

protected VT200Terminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();

        // VT200 adds SGR code 52 (video reverse) and 55 (video normal).
        if (finalByte == 'm') {
            handleVt200SGR(params);
            return;
        }

        super.handleCSI(params);
    }

    /**
     * Handle VT200 SGR — processes VT200 codes (52/55) and delegates
     * standard codes to the parent chain (VT100).
     *
     * <p>SGR 52 (video reverse) and SGR 55 (video normal) map to the
     * reverse attribute in TermAttr, consistent with SGR 7/27.
     */
    private void handleVt200SGR(CSIParams params) {
        // Separate VT200-specific codes from standard codes
        List<Integer> standardCodes = new ArrayList<>();
        boolean hasVideoReverse = false;
        boolean hasVideoNormal = false;

        for (int i = 0; i < params.size(); i++) {
            int code = params.get(i);
            if (code == 52) {
                videoReverse = true;
                hasVideoReverse = true;
            } else if (code == 55) {
                videoReverse = false;
                hasVideoNormal = true;
            } else {
                standardCodes.add(code);
            }
        }

        // If no VT200-specific codes, delegate all to parent
        if (!hasVideoReverse && !hasVideoNormal) {
            super.handleCSI(params);
            return;
        }

        // Delegate standard codes to parent chain first
        if (!standardCodes.isEmpty()) {
            CSIParams filtered = new CSIParams(standardCodes, params.intermediates(), params.finalByte());
            super.handleCSI(filtered);
        }

        // Apply VT200 video reverse/normal on top of parent-processed result
        TermAttr current = display().currentAttr();
        TermAttr.Builder builder = current.toBuilder();
        if (hasVideoReverse) builder.reverse(true);
        if (hasVideoNormal) builder.reverse(false);
        display().setCurrentAttr(builder.build());
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
