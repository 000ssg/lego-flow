package ssg.legoflow.network.terminals.xterm;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.ansi.ANSITerminal;

/**
 * XTERM terminal emulator.
 *
 * <p>Extends ANSI/VT100 with modern terminal features:
 * <ul>
 *   <li>256-color palette (SGR 38;5;n / 48;5;n)</li>
 *   <li>True color RGB (SGR 38;2;r;g;b / 48;2;r;g;b)</li>
 *   <li>Mouse tracking modes (DECSET 1000–1006, 1015–1016)</li>
 *   <li>Bracketed paste mode (DECSET 2024)</li>
 *   <li>Synchronized output mode (DECSET 2026)</li>
 *   <li>Focus event tracking (DECSET 1004)</li>
 *   <li>Underline styles (SGR 4:0–4:5)</li>
 *   <li>Extended text decorations (SGR 53 — overline)</li>
 *   <li>Icon/window title (OSC 0, 1, 2)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class XTERMTerminal extends ANSITerminal {

    public static Terminal create(TerminalConfig config) {
        return new XTERMTerminal(config);
    }

    // Mouse tracking modes
    public enum MouseMode {
        OFF,       // No mouse tracking
        NORMAL,    // DECSET 1000 — button event tracking
        HIGHLIGHT, // DECSET 1002 — highlight tracking
        CELL_MOTION // DECSET 1003 — all motion tracking
    }

    private MouseMode mouseMode = MouseMode.OFF;
    private boolean sgrMouse;         // DECSET 1006 — SGR extended mouse
    private boolean urxvtMouse;       // DECSET 1015 — URXVT mouse mode
    private boolean bracketedPaste;   // DECSET 2024 — bracketed paste
    private boolean syncMode;         // DECSET 2026 — synchronized output
    private boolean focusTracking;    // DECSET 1004 — focus event tracking
    private boolean overline;         // SGR 53 — overline
    private int cursorStyle;          // 0=default, 1=block, 2=underline, 3=bar

    private XTERMTerminal(TerminalConfig config) {
        super(config);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // XTERM re-enables DEC private modes that it supports
        if (intermediates.equals("?")) {
            handleXtermDecPrivate(params);
            return;
        }

        // Handle XTERM-specific CSI sequences
        if (finalByte == 'm') {
            handleXtermSGR(params);
            return;
        }

        // Delegate to parent (ANSI/VT100)
        super.handleCSI(params);
    }

    /** Handle XTERM-specific DEC private modes. */
    private void handleXtermDecPrivate(CSIParams params) {
        char finalByte = params.finalByte();

        if (finalByte == 'h') {
            for (int i = 0; i < params.size(); i++) {
                enableDecPrivateMode(params.get(i));
            }
        } else if (finalByte == 'l') {
            for (int i = 0; i < params.size(); i++) {
                disableDecPrivateMode(params.get(i));
            }
        } else {
            // Delegate unknown DEC private modes to VT100 handler
            super.handleCSI(params);
        }
    }

    private void enableDecPrivateMode(int mode) {
        switch (mode) {
            case 1 -> {/* DECCM — application cursor keys */}
            case 5 -> {/* DECSCNM — reverse video */}
            case 6 -> display().setOriginMode(true);    // DECORM — origin mode
            case 7 -> {/* DECAWM — auto-wrap */}
            case 40 -> {/* DECCOLM — smooth scroll */}
            case 1000 -> mouseMode = MouseMode.NORMAL;
            case 1001 -> {/* DECSET 1001 — mouse in alt screen */}
            case 1002 -> mouseMode = MouseMode.HIGHLIGHT;
            case 1003 -> mouseMode = MouseMode.CELL_MOTION;
            case 1004 -> focusTracking = true;            // Focus event tracking
            case 1005 -> {/* URXVT mouse — superseded by 1006 */}
            case 1006 -> sgrMouse = true;                 // SGR extended mouse
            case 1015 -> urxvtMouse = true;               // URXVT mouse
            case 1016 -> sgrMouse = true;                 // SGR mouse alias
            case 2024 -> bracketedPaste = true;           // Bracketed paste
            case 2026 -> syncMode = true;                 // Synchronized output
            default -> {/* Unknown — delegate to VT100 */}
        }
    }

    private void disableDecPrivateMode(int mode) {
        switch (mode) {
            case 1 -> {/* DECCM — numeric cursor keys */}
            case 5 -> {/* DECSCNM — normal video */}
            case 6 -> display().setOriginMode(false);     // DECORM off
            case 7 -> {/* DECAWM off — no auto-wrap */}
            case 40 -> {/* DECCOLM off */}
            case 1000, 1002, 1003 -> mouseMode = MouseMode.OFF;
            case 1004 -> focusTracking = false;
            case 1005, 1015 -> urxvtMouse = false;
            case 1006, 1016 -> sgrMouse = false;
            case 2024 -> bracketedPaste = false;
            case 2026 -> syncMode = false;
            default -> {/* Unknown — ignore */}
        }
    }

    /** Handle XTERM-specific SGR codes. */
    private void handleXtermSGR(CSIParams params) {
        TermAttr.Builder builder = display().currentAttr().toBuilder();
        int i = 0;
        while (i < params.size()) {
            int code = params.get(i);
            switch (code) {
                case 0 -> builder.reset();
                case 1 -> builder.bold(true);
                case 2 -> builder.dim(true);
                case 3 -> builder.italic(true);
                case 4 -> {
                    if (i + 1 < params.size()) {
                        int style = params.get(i + 1);
                        if (style >= 0 && style <= 5) {
                            builder.underline(style);
                        }
                        i++; // skip sub-param
                    }
                }
                case 5 -> builder.blink(true);
                case 6 -> {/* reserved */}
                case 7 -> builder.reverse(true);
                case 8 -> builder.hidden(true);
                case 9 -> builder.strikethrough(true);
                case 22 -> builder.bold(false).dim(false);
                case 23 -> builder.italic(false);
                case 24 -> builder.underline(TermAttr.UNDERLINE_NONE);
                case 25 -> builder.blink(false);
                case 27 -> builder.reverse(false);
                case 28 -> builder.hidden(false);
                case 29 -> builder.strikethrough(false);
                case 53 -> overline = true;
                case 55 -> overline = false;

                // Basic foreground colors (30-37)
                case 30, 31, 32, 33, 34, 35, 36, 37 ->
                        builder.foreground(code - 30);
                case 38 -> i += handleExtendedForeground(builder, params, i);
                case 39 -> builder.foreground(TermAttr.WHITE);

                // Basic background colors (40-47)
                case 40, 41, 42, 43, 44, 45, 46, 47 ->
                        builder.background(code - 40);
                case 48 -> i += handleExtendedBackground(builder, params, i);
                case 49 -> builder.background(TermAttr.BLACK);

                // Bright foreground (90-97)
                case 90, 91, 92, 93, 94, 95, 96, 97 ->
                        builder.foreground(code - 82);

                // Bright background (100-107)
                case 100, 101, 102, 103, 104, 105, 106, 107 ->
                        builder.background(code - 92);

                default -> {/* Unknown SGR — ignore */}
            }
            i++;
        }
        display().setCurrentAttr(builder.build());
    }

    private int handleExtendedForeground(TermAttr.Builder builder, CSIParams params, int idx) {
        if (idx + 1 >= params.size()) return 0;
        int mode = params.get(idx + 1);
        if (mode == 5 && idx + 2 < params.size()) {
            // 256-color: 38;5;<index>
            builder.foreground256(params.get(idx + 2));
            return 2;
        } else if (mode == 2 && idx + 4 < params.size()) {
            // True color: 38;2;<r>;<g>;<b>
            int r = params.get(idx + 2);
            int g = params.get(idx + 3);
            int b = params.get(idx + 4);
            builder.foregroundRgb((r << 16) | (g << 8) | b);
            return 4;
        }
        return 0;
    }

    private int handleExtendedBackground(TermAttr.Builder builder, CSIParams params, int idx) {
        if (idx + 1 >= params.size()) return 0;
        int mode = params.get(idx + 1);
        if (mode == 5 && idx + 2 < params.size()) {
            // 256-color: 48;5;<index>
            builder.background256(params.get(idx + 2));
            return 2;
        } else if (mode == 2 && idx + 4 < params.size()) {
            // True color: 48;2;<r>;<g>;<b>
            int r = params.get(idx + 2);
            int g = params.get(idx + 3);
            int b = params.get(idx + 4);
            builder.backgroundRgb((r << 16) | (g << 8) | b);
            return 4;
        }
        return 0;
    }

    @Override
    public void handleDCS(String data) {
        // XTERM DCS sequences
        if (data.endsWith("p")) {
            // DECRQSS — request status string
            String request = data.replace("p", "");
            // XTERM responds with the current setting
        }
    }

    @Override
    public String type() { return "xterm"; }

    @Override
    public boolean supportsColor() { return true; }

    @Override
    protected void onReset() {
        super.onReset();
        mouseMode = MouseMode.OFF;
        sgrMouse = false;
        urxvtMouse = false;
        bracketedPaste = false;
        syncMode = false;
        focusTracking = false;
        overline = false;
        cursorStyle = 0;
    }

    // --- Property accessors ---

    public MouseMode mouseMode() { return mouseMode; }
    public boolean isSgrMouse() { return sgrMouse; }
    public boolean isUrxvtMouse() { return urxvtMouse; }
    public boolean isBracketedPaste() { return bracketedPaste; }
    public boolean isSyncMode() { return syncMode; }
    public boolean isFocusTracking() { return focusTracking; }
    public boolean isOverline() { return overline; }
    public int cursorStyle() { return cursorStyle; }
}
