package ssg.legoflow.network.terminals.xterm;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.ansi.ANSITerminal;

import java.util.Base64;

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
 *   <li>Cursor shape control (CSI Ps SP q — DECSCUSR)</li>
 *   <li>Clipboard manipulation (OSC 52)</li>
 *   <li>Icon/window title (OSC 0, 1, 2)</li>
 *   <li>Color theme queries (OSC 10–19)</li>
 * </ul>
 *
 * <p>Known limitations:
 * <ul>
 *   <li>Mouse reports are stateful only — no actual mouse event generation</li>
 *   <li>No window geometry queries — XTWINOP not implemented</li>
 *   <li>No permit windowOps — DECSET 1003/1010/1011 not implemented</li>
 *   <li>No debug mode — DECSET 1010 not implemented</li>
 *   <li>No send escape sequence back — DECSET 1011 not implemented</li>
 *   <li>No multimedia keys — DECSET 1030+ not implemented</li>
 *   <li>No clipboard read — only write (OSC 52) supported</li>
 *   <li>SGR 58/59 (border color) not supported</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class XTERMTerminal extends ANSITerminal {

    public static Terminal create(TerminalConfig config) {
        return new XTERMTerminal(config);
    }

    /** Mouse tracking modes. */
    public enum MouseMode {
        OFF,       // No mouse tracking
        NORMAL,    // DECSET 1000 — button event tracking
        HIGHLIGHT, // DECSET 1002 — highlight tracking
        CELL_MOTION // DECSET 1003 — all motion tracking
    }

    /** Cursor shape styles. */
    public enum CursorStyle {
        DEFAULT(0),   // Default
        BLINK_BLOCK(1),
        STEADY_BLOCK(2),
        BLINK_UNDERLINE(3),
        STEADY_UNDERLINE(4),
        BLINK_BAR(5),
        STEADY_BAR(6);

        private final int code;
        CursorStyle(int code) { this.code = code; }
        public int code() { return code; }
    }

    private MouseMode mouseMode = MouseMode.OFF;
    private boolean sgrMouse;         // DECSET 1006 — SGR extended mouse
    private boolean urxvtMouse;       // DECSET 1015 — URXVT mouse mode
    private boolean bracketedPaste;   // DECSET 2024 — bracketed paste
    private boolean syncMode;         // DECSET 2026 — synchronized output
    private boolean focusTracking;    // DECSET 1004 — focus event tracking
    private CursorStyle cursorStyle = CursorStyle.DEFAULT;
    private String clipboardData;     // OSC 52 clipboard data
    private String primarySelection;  // OSC 52 primary selection

    
    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("xterm", config -> XTERMTerminal.create(config));
        TerminalFactory.register("xterm-256color", config -> XTERMTerminal.create(config));
    }

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

        // DECSCUSR — cursor shape: CSI Ps SP q (space as intermediate)
        if (intermediates.equals(" ") && finalByte == 'q') {
            int ps = params.get(0, 0);
            switch (ps) {
                case 0 -> cursorStyle = CursorStyle.DEFAULT;
                case 1 -> cursorStyle = CursorStyle.BLINK_BLOCK;
                case 2 -> cursorStyle = CursorStyle.STEADY_BLOCK;
                case 3 -> cursorStyle = CursorStyle.BLINK_UNDERLINE;
                case 4 -> cursorStyle = CursorStyle.STEADY_UNDERLINE;
                case 5 -> cursorStyle = CursorStyle.BLINK_BAR;
                case 6 -> cursorStyle = CursorStyle.STEADY_BAR;
                default -> {/* Unknown — ignore */}
            }
            return;
        }

        // Handle XTERM-specific SGR
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
            case 7 -> {/* DECAWM — auto-wrap (already default) */}
            case 40 -> {/* DECCOLM — smooth scroll */}
            case 1000 -> mouseMode = MouseMode.NORMAL;
            case 1001 -> {/* DECSET 1001 — mouse in alt screen */}
            case 1002 -> mouseMode = MouseMode.HIGHLIGHT;
            case 1003 -> mouseMode = MouseMode.CELL_MOTION;
            case 1004 -> focusTracking = true;
            case 1005 -> {/* URXVT mouse — superseded by 1006 */}
            case 1006 -> sgrMouse = true;
            case 1015 -> urxvtMouse = true;
            case 1016 -> sgrMouse = true;
            case 2004 -> bracketedPaste = true;
            case 2026 -> syncMode = true;
            default -> {/* Unknown — delegate to VT100 */}
        }
    }

    private void disableDecPrivateMode(int mode) {
        switch (mode) {
            case 1 -> {/* DECCM — numeric cursor keys */}
            case 5 -> {/* DECSCNM — normal video */}
            case 6 -> display().setOriginMode(false);
            case 7 -> {/* DECAWM off */}
            case 40 -> {/* DECCOLM off */}
            case 1000, 1002, 1003 -> mouseMode = MouseMode.OFF;
            case 1004 -> focusTracking = false;
            case 1005, 1015 -> urxvtMouse = false;
            case 1006, 1016 -> sgrMouse = false;
            case 2004 -> bracketedPaste = false;
            case 2026 -> syncMode = false;
            default -> {/* Unknown — delegate to VT100 */}
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
                    // Underline: default to single, override if valid style subparam (4:0-4:5)
                    // Note: CSIParams flattens ; and : separators, so CSI 4;1 m and CSI 4:1 m
                    // are indistinguishable. Values 0-5 are treated as style subparams.
                    // This means CSI 4;1 m (underline + bold) is interpreted as underline style 1.
                    if (i + 1 < params.size()) {
                        int next = params.get(i + 1);
                        if (next >= 0 && next <= 5) {
                            builder.underline(next);
                            i++; // skip sub-param
                        } else {
                            builder.underline(TermAttr.UNDERLINE_SINGLE);
                        }
                    } else {
                        builder.underline(TermAttr.UNDERLINE_SINGLE);
                    }
                }
                case 5 -> builder.blink(true);
                case 6 -> {/* reserved — slow blink */}
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

                // Underline reset
                case 53 -> builder.overline(true);
                case 55 -> builder.overline(false);

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

    /**
     * Handle DCS sequences — implements DECRQSS (Request Status String).
     *
     * <p>Format: DCS Ps ; $ q ST
     * Response: DCS Ps ; | Pt ST
     *
     * <p>Supported queries:
     * <ul>
     *   <li>CSI $ q — DECSCUSR (cursor shape)</li>
     *   <li>? 1004 $ p — focus tracking mode</li>
     *   <li>? 1005 $ p — URXVT mouse mode</li>
     *   <li>? 1006 $ p — SGR mouse mode</li>
     *   <li>? 1015 $ p — URXVT mouse mode</li>
     *   <li>? 2004 $ p — bracketed paste mode</li>
     *   <li>? 2026 $ p — synchronized output mode</li>
     * </ul>
     */
    @Override
    public void handleDCS(String data) {
        // DECRQSS format: DCS <Ps> ; $ q ST  (with '$' as intermediate)
        // Check if this is a DECRQSS request (ends with '$q' after params)
        int qIndex = data.indexOf("$q");
        if (qIndex < 0) return;  // Not DECRQSS

        String paramsPart = data.substring(0, qIndex).trim();
        String response = getDecrqssResponse(paramsPart);
        if (response != null) {
            output(response);
        }
    }

    /**
     * Generate DECRQSS response for the given parameter string.
     * Returns null if the query is not supported.
     */
    private String getDecrqssResponse(String params) {
        // CSI $ q — cursor shape (DECSCUSR)
        if (params.equals("$q")) {
            return String.format("Pq; %d$", cursorStyle.code());
        }

        // ? Ps $ p — DEC private mode query
        if (params.startsWith("?")) {
            try {
                int mode = Integer.parseInt(params.substring(1).trim());
                int state = getDecModeState(mode);
                return String.format("P?%d;%d$p", mode, state);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }

    /**
     * Get the state of a DEC private mode for DECRQSS.
     * Returns 1 (set) or 2 (cleared).
     */
    private int getDecModeState(int mode) {
        boolean active = switch (mode) {
            case 1 -> isApplicationKeypad();  // DECCM (from VT100)
            case 5 -> isScreenReverse();      // DECSCNM (from VT100)
            case 6 -> isOriginMode();          // DECORM
            case 7 -> isAutoWrap();            // DECAWM
            case 40 -> isSmoothScroll();       // DECCOLM
            case 1000, 1002, 1003 -> mouseMode != MouseMode.OFF;
            case 1004 -> focusTracking;
            case 1005, 1015 -> urxvtMouse;
            case 1006, 1016 -> sgrMouse;
            case 2004 -> bracketedPaste;
            case 2026 -> syncMode;
            default -> false;
        };
        return active ? 1 : 2;
    }

    @Override
    public void handleOSC(String data) {
        if (data.startsWith("0;") || data.startsWith("2;")) {
            // Window title
            String title = data.substring(2);
            display().setTitle(title);
            fireTitleChange(title);
        } else if (data.startsWith("1;")) {
            // Icon name
            String icon = data.substring(2);
            display().setIconTitle(icon);
        } else if (data.startsWith("52;")) {
            // OSC 52 — clipboard manipulation
            handleClipboardOSC(data.substring(3));
        } else if (data.startsWith("10;") || data.startsWith("11;") || data.startsWith("12;")) {
            // OSC 10/11/12 — color theme queries (recognized but not fully supported)
        } else if (data.startsWith("7;")) {
            // OSC 7 — current working directory (recognized but not implemented)
        } else if (data.startsWith("20;") || data.startsWith("21;")) {
            // OSC 20/21 — XTWINOP window operations (recognized but not implemented)
            // XTERM supports: geometry, icon, title, layer, etc.
        } else {
            // Delegate unknown OSC to parent
            super.handleOSC(data);
        }
    }

    /** Handle OSC 52 clipboard operations. */
    private void handleClipboardOSC(String spec) {
        String[] parts = spec.split(";", 2);
        if (parts.length < 2) return;

        String target = parts[0];
        String base64Data = parts[1];

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Data);
            String text = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);

            if ("?".equals(target) || "CLIPBOARD".equalsIgnoreCase(target)) {
                this.clipboardData = text;
            } else if ("PRIMARY".equalsIgnoreCase(target) || "p".equals(target)) {
                this.primarySelection = text;
            } else if ("S".equalsIgnoreCase(target)) {
                // Secondary selection — not distinguished from primary
                this.primarySelection = text;
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid base64 — ignore
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
        cursorStyle = CursorStyle.DEFAULT;
        clipboardData = null;
        primarySelection = null;
    }

    // --- Property accessors ---

    /** Current mouse tracking mode. */
    public MouseMode mouseMode() { return mouseMode; }

    /** Whether SGR extended mouse reporting is enabled. */
    public boolean isSgrMouse() { return sgrMouse; }

    /** Whether URXVT mouse mode is enabled. */
    public boolean isUrxvtMouse() { return urxvtMouse; }

    /** Whether bracketed paste mode is enabled. */
    public boolean isBracketedPaste() { return bracketedPaste; }

    /** Whether synchronized output mode is enabled. */
    public boolean isSyncMode() { return syncMode; }

    /** Whether focus event tracking is enabled. */
    public boolean isFocusTracking() { return focusTracking; }

    /** Current cursor shape style. */
    public CursorStyle cursorStyle() { return cursorStyle; }

    /** Clipboard data set via OSC 52. */
    public String clipboardData() { return clipboardData; }

    /** Primary selection set via OSC 52. */
    public String primarySelection() { return primarySelection; }

    /** Whether overline is currently active. */
    public boolean isOverline() { return display().currentAttr().overline(); }
}
