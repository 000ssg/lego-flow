package ssg.legoflow.network.terminals.vt100;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.Screen;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.io.AbstractTerminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;
import ssg.legoflow.network.terminals.base.io.Terminal;
/**
 * VT100 terminal emulator.
 *
 * <p>Implements the full VT100 protocol including:
 * <ul>
 *   <li>CSI cursor motion (CUU, CUD, CUF, CUB, CUP, HVP, CHA, VPA, CNL, CPL)</li>
 *   <li>SGR text attributes (0–9, 22–29, 30–37, 40–47, 90–97, 100–107)</li>
 *   <li>DECSET/DECRST for DEC private modes (origin mode, auto-wrap)</li>
 *   <li>DECKPAM/DECKPNM (application/numeric keypad)</li>
 *   <li>Device attributes (DA1)</li>
 *   <li>Scroll region (DECSTBM)</li>
 *   <li>Line operations (IL, DL)</li>
 *   <li>Character operations (ICH, DCH, ECH)</li>
 *   <li>Cursor save/restore (DECSC/DECRC)</li>
 *   <li>Tab stop management (HTS, TBC)</li>
 *   <li>Repeat preceding character (EUT)</li>
 *   <li>ESC # n sequences (reverse video, bold)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class VT100Terminal extends AbstractTerminal {

    public static Terminal create(TerminalConfig config) {
        return new VT100Terminal(config);
    }

    private Cursor savedCursor;
    private TermAttr savedAttr = TermAttr.DEFAULT;
    private boolean autoWrap = true;
    private boolean applicationKeypad;
    private boolean screenReverse;   // DECSCNM (mode 5) — reverse video
    private boolean smoothScroll;    // DECCOLM (mode 40) — smooth scroll

    
    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("vt100", config -> VT100Terminal.create(config));
        TerminalFactory.register("vt-100", config -> VT100Terminal.create(config));
    }

protected VT100Terminal(TerminalConfig config) {
        super(config);
        this.autoWrap = config.autoWrap();
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // Handle DECRQM — query DEC private mode (ESC [ ? Ps $ p)
        if (intermediates.equals("?$") && finalByte == 'p') {
            handleDecrqm(params);
            return;
        }

        // Handle DEC private sequences (ESC [ ? ...)
        if (intermediates.equals("?")) {
            handleDecPrivate(params);
            return;
        }

        // Handle standard CSI sequences
        switch (finalByte) {
            case 'J' -> eraseDisplay(params);                             // ED
            case 'K' -> eraseLine(params);                                // EL
            case 'L' -> display().screen().insertLines(params.get(0, 1)); // IL
            case 'M' -> display().screen().deleteLines(params.get(0, 1)); // DL
            case 'P' -> display().screen().deleteChars(params.get(0, 1)); // DCH
            case '@' -> display().screen().insertChars(params.get(0, 1)); // ICH
            case 'X' -> eraseChar(params);                                // ECH
            case 'm' -> selectGraphicRendition(params);                   // SGR
            case 'd' -> cursorVerticalAbsolute(params.get(0, 1));         // VPA
            case 'b' -> repeatPreceding(params);                          // EUT
            case '7' -> saveCursor();                                     // DECSC (CSI variant)
            case '8' -> restoreCursor();                                  // DECRC (CSI variant)
            case 'r' -> setScrollRegion(params);                          // DECSTBM
            case 's' -> saveCursor();                                     // DECSC (alternate)
            case 'u' -> restoreCursor();                                  // DECRC (alternate)
            case 'n' -> deviceStatus(params);                             // DSR
            default -> super.handleCSI(params);
        }
    }

    @Override
    public void handleEscSequence(char letter) {
        switch (letter) {
            case '7' -> saveCursor();   // DECSC — save cursor
            case '8' -> restoreCursor(); // DECRC — restore cursor
            default -> super.handleEscSequence(letter);
        }
    }

    @Override
    public void handleEscHash(char digit) {
        switch (digit) {
            case '3' -> {/* DECSED — reversed video (display-dependent, no-op in emulator) */}
            case '8' -> {/* DECDBL — bold (display-dependent, no-op in emulator) */}
            default -> super.handleEscHash(digit);
        }
    }

    private void handleDecPrivate(CSIParams params) {
        char finalByte = params.finalByte();

        switch (finalByte) {
            case 'h' -> decset(params);
            case 'l' -> decrst(params);

            case 'c' -> deviceAttributes();                              // DA1
            default -> {/* Unknown DEC private sequence */}
        }
    }

    private void decset(CSIParams params) {
        for (int i = 0; i < params.size(); i++) {
            int mode = params.get(i);
            switch (mode) {
                case 1 -> applicationKeypad = true;  // DECCM — cursor key mode
                case 5 -> {
                    screenReverse = true;
                    // DECSCNM: reverse video — apply to current attributes
                    TermAttr current = display().currentAttr();
                    display().setCurrentAttr(current.toBuilder().reverse(true).build());
                }
                case 6 -> display().setOriginMode(true); // DECORM
                case 7 -> autoWrap = true;             // DECAWM
                case 40 -> {
                    smoothScroll = true;
                    // DECCOLM: 80-column mode — clear screen, reset cursor
                    // Note: cannot change column count (config immutable);
                    // clear screen is the primary visual effect
                    display().screen().clear();
                    display().cursor().setPos(1, 1);
                }
                default -> {/* Unknown mode — ignore */}
            }
        }
    }

    private void decrst(CSIParams params) {
        for (int i = 0; i < params.size(); i++) {
            int mode = params.get(i);
            switch (mode) {
                case 1 -> applicationKeypad = false;  // DECCM
                case 5 -> {
                    screenReverse = false;
                    // DECSCNM: disable reverse video
                    TermAttr current = display().currentAttr();
                    if (current.reverse()) {
                        display().setCurrentAttr(current.toBuilder().reverse(false).build());
                    }
                }
                case 6 -> display().setOriginMode(false); // DECORM
                case 7 -> autoWrap = false;            // DECAWM
                case 40 -> smoothScroll = false; // DECCOLM off
                default -> {/* Unknown mode — ignore */}
            }
        }
    }

    /**
     * Device Attributes (DA1) — CSI ? c.
     * Responds with VT100 identifier via output buffer.
     */
    private void deviceAttributes() {
        // VT100 with auto-wrap: ESC [ ? 1 ; 2 c
        output("[?1;2c");
    }

    /**
     * Query DEC Private Mode (DECRQM) — ESC [ ? Ps $ p.
     * Responds with: ESC [ ? Ps ; Pb $ y
     *
     * <p>Pb states: 0=not recognized, 1=set(permanent), 2=set(clearable),
     * 3=cleared(settable), 4=cleared(permanent).
     */
    private void handleDecrqm(CSIParams params) {
        int mode = params.get(0, 0);
        int state = getDecModeState(mode);
        output(String.format("[?%d;%d$y", mode, state));
    }

    /**
     * Get the state of a DEC private mode.
     * Returns 2 (set, clearable) or 3 (cleared, settable).
     */
    private int getDecModeState(int mode) {
        return switch (mode) {
            case 1 -> applicationKeypad ? 2 : 3;  // DECCM
            case 5 -> screenReverse ? 2 : 3;       // DECSCNM
            case 6 -> display().originMode() ? 2 : 3;  // DECORM
            case 7 -> autoWrap ? 2 : 3;            // DECAWM
            case 40 -> smoothScroll ? 2 : 3;       // DECCOLM
            default -> 0;  // Not recognized
        };
    }

    /**
     * Device Status Report (DSR) — CSI Ps n.
     * Generates responses that the transport layer reads via readOutput().
     */
    private void deviceStatus(CSIParams params) {
        int mode = params.get(0, 0);
        switch (mode) {
            case 0 -> {
                // DA1 — Device Attributes: VT100 with auto-wrap
                output("[?1;2c");
            }
            case 5 -> {
                // Device Status Report (OK): ESC [ 0 n
                output("[0n");
            }
            case 6 -> {
                // Cursor Position Report (CPR): ESC [ r ; c R
                Cursor cur = display().cursor();
                output(String.format("[%d;%dR", cur.row(), cur.col()));
            }
            default -> {/* Unknown DSR mode — ignore */}
        }
    }

    private void setScrollRegion(CSIParams params) {
        int top = params.get(0, 1);
        int bottom = params.get(1, config().rows());
        // Validate: top must be <= bottom
        if (top > bottom) {
            top = 1;
            bottom = config().rows();
        }
        display().screen().setScrollRegion(top, bottom);
        display().cursor().setPos(1, 1);
    }

    private void eraseDisplay(CSIParams params) {
        display().eraseDisplay(params.get(0, 0));
    }

    private void eraseLine(CSIParams params) {
        display().eraseLine(params.get(0, 0));
    }

    private void eraseChar(CSIParams params) {
        display().screen().eraseChars(params.get(0, 1));
    }

    private void repeatPreceding(CSIParams params) {
        int count = params.get(0, 1);
        Screen screen = display().screen();
        Cursor cur = display().cursor();
        // Get the preceding character (one column to the left of cursor)
        int prevCol = Math.max(1, cur.col() - 1);
        var ch = screen.at(cur.row(), prevCol);
        // EUT repeats the preceding character count+1 times from current position
        for (int i = 0; i < count + 1; i++) {
            screen.put(ch);
        }
    }

    private void selectGraphicRendition(CSIParams params) {
        TermAttr.Builder builder = display().currentAttr().toBuilder();
        int i = 0;
        while (i < params.size()) {
            int code = params.get(i);
            switch (code) {
                case 0 -> builder.reset();
                case 1 -> builder.bold(true);
                case 2 -> builder.dim(true);
                case 3 -> builder.italic(true);
                case 4 -> builder.underline(TermAttr.UNDERLINE_SINGLE);
                case 5 -> builder.blink(true);
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
                case 30, 31, 32, 33, 34, 35, 36, 37 ->
                        builder.foreground(code - 30);
                case 39 -> builder.foreground(TermAttr.WHITE);
                case 40, 41, 42, 43, 44, 45, 46, 47 ->
                        builder.background(code - 40);
                case 49 -> builder.background(TermAttr.BLACK);
                case 90, 91, 92, 93, 94, 95, 96, 97 ->
                        builder.foreground(code - 82);
                case 100, 101, 102, 103, 104, 105, 106, 107 ->
                        builder.background(code - 92);
                default -> {/* Unknown SGR — ignore */}
            }
            i++;
        }
        display().setCurrentAttr(builder.build());
    }

    private void saveCursor() {
        savedCursor = display().cursor().clone();
        savedAttr = display().currentAttr();
    }

    private void restoreCursor() {
        if (savedCursor != null) {
            display().cursor().setPos(savedCursor.row(), savedCursor.col());
            display().setCurrentAttr(savedAttr);
        }
    }

    @Override
    public void handleOSC(String data) {
        if (data.startsWith("0;") || data.startsWith("2;")) {
            String title = data.substring(2);
            display().setTitle(title);
            fireTitleChange(title);
        } else if (data.startsWith("1;")) {
            String icon = data.substring(2);
            display().setIconTitle(icon);
        }
    }

    @Override
    public String type() { return "vt100"; }

    @Override
    public boolean supportsColor() { return true; }

    @Override
    protected void onReset() {
        display().setOriginMode(false);
        autoWrap = true;
        applicationKeypad = false;
        screenReverse = false;
        smoothScroll = false;
        savedCursor = null;
        savedAttr = TermAttr.DEFAULT;
        display().setCurrentAttr(TermAttr.DEFAULT);
    }

    /** Check if origin mode is active. */
    public boolean isOriginMode() { return display().originMode(); }

    /** Check if auto-wrap is active. */
    public boolean isAutoWrap() { return autoWrap; }

    /** Check if application keypad mode is active. */
    public boolean isApplicationKeypad() { return applicationKeypad; }

    /** Check if screen reverse mode (DECSCNM) is active. */
    public boolean isScreenReverse() { return screenReverse; }

    /** Check if smooth scroll mode (DECCOLM) is active. */
    public boolean isSmoothScroll() { return smoothScroll; }
}
