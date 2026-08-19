package ssg.legoflow.network.terminals.vt400;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.vt200.VT200Terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * VT400 terminal emulator.
 *
 * <p>Extends VT200 with workstation capabilities:
 * <ul>
 *   <li>Extended SGR codes (82–89: extended foreground, 92–99: extended background)</li>
 *   <li>Multiple window support (4 logical windows)</li>
 *   <li>Window size control via OSC 14</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Window selection is logical only — no physical screen splitting</li>
 *   <li>DECCOM commodity codes not implemented</li>
 *   <li>No window-specific scroll regions</li>
 *   <li>No vertical/horizontal margin mode</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class VT400Terminal extends VT200Terminal {

    public static Terminal create(TerminalConfig config) {
        return new VT400Terminal(config);
    }

    private int activeWindow = 1;
    private final int windowCount = 4;
    private String osc14BgColor;

    
    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("vt400", config -> VT400Terminal.create(config));
    }

protected VT400Terminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // Window selection: CSI <window> t
        if (finalByte == 't') {
            int win = params.get(0, 1);
            activeWindow = Math.max(1, Math.min(win, windowCount));
            return;
        }

        // SGR — intercept to handle VT400 extended codes (82-89, 92-99)
        // before delegating standard codes to the parent chain.
        if (finalByte == 'm') {
            handleVt400SGR(params);
            return;
        }

        // DEC private modes — VT400 inherits DECSET/DECRST from VT100
        if (intermediates.equals("?")) {
            super.handleCSI(params);
            return;
        }

        super.handleCSI(params);
    }

    /**
     * Handle VT400 SGR — processes extended codes (82-89, 92-99) locally,
     * then delegates remaining codes to the parent chain (VT200 → VT100).
     *
     * <p>Critical: VT400 extended codes must be applied in the same pass
     * as standard codes to avoid the parent chain overriding them.
     * The parent chain (VT200 → VT100) calls display().setCurrentAttr()
     * which would overwrite any attributes set before the delegation.
     * To fix this, we filter out VT400-specific codes from the params
     * before delegating, then apply the extended codes after the parent
     * has processed standard codes.
     */
    private void handleVt400SGR(CSIParams params) {
        // Collect VT400 extended codes and build filtered param list
        List<Integer> standardCodes = new ArrayList<>();
        int lastFg = -1;  // Track last extended foreground code
        int lastBg = -1;  // Track last extended background code

        for (int i = 0; i < params.size(); i++) {
            int code = params.get(i);
            if (code >= 82 && code <= 89) {
                lastFg = code - 82;  // Map 82-89 → 0-7
            } else if (code >= 92 && code <= 99) {
                lastBg = code - 92;  // Map 92-99 → 0-7
            } else {
                standardCodes.add(code);
            }
        }

        // If no extended codes, just delegate to parent
        if (lastFg == -1 && lastBg == -1) {
            super.handleCSI(params);
            return;
        }

        // Delegate standard codes to parent chain first
        if (!standardCodes.isEmpty()) {
            CSIParams filtered = new CSIParams(standardCodes, params.intermediates(), params.finalByte());
            super.handleCSI(filtered);
        }

        // Now apply VT400 extended codes on top of parent-processed result
        TermAttr current = display().currentAttr();
        TermAttr.Builder builder = current.toBuilder();
        if (lastFg >= 0) builder.foreground(lastFg);
        if (lastBg >= 0) builder.background(lastBg);
        display().setCurrentAttr(builder.build());
    }

    @Override
    public void handleOSC(String data) {
        if (data.startsWith("14;")) {
            // OSC 14;RRGGBB — set default background color
            String color = data.substring(3);
            if (color.length() >= 6) {
                try {
                    Integer.parseInt(color.substring(0, 2), 16);
                    Integer.parseInt(color.substring(2, 4), 16);
                    Integer.parseInt(color.substring(4, 6), 16);
                    osc14BgColor = color;
                } catch (NumberFormatException ignored) {
                    // Invalid color format — ignore
                }
            }
        } else {
            super.handleOSC(data);
        }
    }

    @Override
    public String type() { return "vt400"; }

    @Override
    public boolean supportsColor() { return true; }

    /** Currently active window number (1-based). */
    public int activeWindow() { return activeWindow; }

    /** Number of supported windows. */
    public int windowCount() { return windowCount; }

    /** Background color set via OSC 14 (hex string, e.g. "aabbcc"). */
    public String osc14BgColor() { return osc14BgColor; }

    @Override
    protected void onReset() {
        super.onReset();
        activeWindow = 1;
        osc14BgColor = null;
    }
}
