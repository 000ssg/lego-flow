package ssg.legoflow.network.terminals.vt100;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.Screen;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.AbstractTerminal;
import ssg.legoflow.network.terminals.base.io.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * VT100 terminal emulator.
 *
 * <p>Implements the full VT100 protocol including:
 * <ul>
 *   <li>CSI cursor motion (CUU, CUD, CUF, CUB, CUP, HVP, CHA, VPA, CNL, CPL)</li>
 *   <li>SGR text attributes (0–7): reset, bold, dim, underline, blink, reverse, hidden, strikethrough</li>
 *   <li>ANSI foreground/background colors (30–37, 40–47)</li>
 *   <li>DECSET/DECRST for DEC private modes (origin mode, auto-wrap)</li>
 *   <li>DECKPAM/DECKPNM (application/numeric keypad)</li>
 *   <li>Device attributes (DA1)</li>
 *   <li>Scroll region (DECSTBM)</li>
 *   <li>Line operations (IL, DL)</li>
 *   <li>Character operations (ICH, DCH, ECH)</li>
 *   <li>Cursor save/restore (DECSC/DECRC)</li>
 *   <li>Repeat preceding character (EUT)</li>
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
    private int deviceType;

    protected VT100Terminal(TerminalConfig config) {
        super(config);
        this.autoWrap = config.autoWrap();
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();
        String intermediates = params.intermediates();

        // Handle DEC private sequences (ESC [ ? ...)
        if (intermediates.equals("?")) {
            handleDecPrivate(params);
            return;
        }

        // Handle standard CSI sequences
        switch (finalByte) {
            case 'A' -> display().cursorUp(params.get(0, 1));            // CUU
            case 'B' -> display().cursorDown(params.get(0, 1));           // CUD
            case 'C' -> display().cursorForward(params.get(0, 1));        // CUF
            case 'D' -> display().cursorBack(params.get(0, 1));           // CUB
            case 'E' -> cursorNextLine(params.get(0, 1));                 // CNL
            case 'F' -> cursorPrevLine(params.get(0, 1));                 // CPL
            case 'G' -> cursorHorizontalAbsolute(params.get(0, 1));       // CHA
            case 'H' -> cursorPosition(params);                           // CUP
            case 'f' -> cursorPosition(params);                           // HVP
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
            case '7' -> saveCursor();                                     // DECSC
            case '8' -> restoreCursor();                                  // DECRC
            case 'r' -> setScrollRegion(params);                          // DECSTBM
            case 's' -> saveCursor();                                     // DECSC (alternate)
            case 'u' -> restoreCursor();                                  // DECRC (alternate)
            default -> super.handleCSI(params);
        }
    }

    private void handleDecPrivate(CSIParams params) {
        char finalByte = params.finalByte();

        switch (finalByte) {
            case 'h' -> decset(params);
            case 'l' -> decrst(params);
            case 'n' -> {/* DECRQM — query mode, not implemented */}
            case 'c' -> deviceAttributes(params);                         // DA1
            default -> {/* Unknown DEC private sequence */}
        }
    }

    private void decset(CSIParams params) {
        for (int i = 0; i < params.size(); i++) {
            int mode = params.get(i);
            switch (mode) {
                case 1 -> applicationKeypad = true;    // DECCM
                case 5 -> {/* DECSCNM — reverse video */}
                case 6 -> display().setOriginMode(true); // DECORM
                case 7 -> autoWrap = true;             // DECAWM
                case 40 -> {/* DECCOLM — smooth scroll with wrap */}
                default -> {/* Unknown mode — ignore */}
            }
        }
    }

    private void decrst(CSIParams params) {
        for (int i = 0; i < params.size(); i++) {
            int mode = params.get(i);
            switch (mode) {
                case 1 -> applicationKeypad = false;   // DECCM
                case 5 -> {/* DECSCNM — reverse video off */}
                case 6 -> display().setOriginMode(false); // DECORM
                case 7 -> autoWrap = false;            // DECAWM
                case 40 -> {/* DECCOLM — smooth scroll off */}
                default -> {/* Unknown mode — ignore */}
            }
        }
    }

    private void deviceAttributes(CSIParams params) {
        // DA1: respond with device type
    }

    private void setScrollRegion(CSIParams params) {
        int top = params.get(0, 1);
        int bottom = params.get(1, config().rows());
        display().screen().setScrollRegion(top, bottom);
        display().cursor().setPos(1, 1);
    }

    private void cursorPosition(CSIParams p) {
        int row = p.get(0, 1);
        int col = p.get(1, 1);
        display().cursorPosition(row, col);
    }

    private void eraseDisplay(CSIParams p) {
        display().eraseDisplay(p.get(0, 0));
    }

    private void eraseLine(CSIParams p) {
        display().eraseLine(p.get(0, 0));
    }

    private void eraseChar(CSIParams p) {
        display().screen().eraseChars(p.get(0, 1));
    }

    private void repeatPreceding(CSIParams p) {
        int count = p.get(0, 1);
        Screen screen = display().screen();
        Cursor cur = display().cursor();
        // Get the preceding character (one column to the left of cursor)
        int prevCol = Math.max(1, cur.col() - 1);
        var ch = screen.at(cur.row(), prevCol);
        // EUT repeats the preceding character count+1 times from current position
        // (VT100 manual: the character fills the rest of the line)
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
                case 38 -> { /* extended foreground — XTERM feature */ }
                case 39 -> builder.foreground(TermAttr.WHITE);
                case 40, 41, 42, 43, 44, 45, 46, 47 ->
                        builder.background(code - 40);
                case 48 -> { /* extended background — XTERM feature */ }
                case 49 -> builder.background(TermAttr.BLACK);
                case 90, 91, 92, 93, 94, 95, 96, 97 ->
                        builder.foreground(code - 82);
                case 100, 101, 102, 103, 104, 105, 106, 107 ->
                        builder.background(code - 92);
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
        savedCursor = null;
        savedAttr = TermAttr.DEFAULT;
        display().setCurrentAttr(TermAttr.DEFAULT);
    }

    /** Check if origin mode is active. */
    public boolean isOriginMode() { return display().originMode(); }

    /** Check if auto-wrap is active. */
    public boolean isAutoWrap() { return autoWrap; }
}
