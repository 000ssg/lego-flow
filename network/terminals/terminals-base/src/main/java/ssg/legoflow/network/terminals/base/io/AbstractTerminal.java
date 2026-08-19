package ssg.legoflow.network.terminals.base.io;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.escape.EscapeParser;
import ssg.legoflow.network.terminals.base.escape.CSIParams;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
/**
 * Base class for terminal emulator implementations.
 *
 * <p>Provides common infrastructure: display model, escape parser,
 * event listeners, and configuration management. Subclasses override
 * the CSI/DCS/OSC handlers to implement type-specific behavior.
 *
 * @since 0.2.0
 */
public abstract class AbstractTerminal implements Terminal, EscapeParser.SequenceHandler {

    private static final int ESC = 0x1B;
    private static final int BEL = 0x07;

    private final TerminalConfig config;
    private final DisplayModel display;
    private final List<TerminalEventListener> listeners;
    private final EscapeParser parser;

    /** Tab stops (1-based column indices). Default: every 8 columns. */
    private final BitSet tabStops;

    /** Buffer for terminal-generated output (DSR responses, DECRQM, etc.). */
    private final StringBuilder outputBuffer = new StringBuilder();

    protected AbstractTerminal(TerminalConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.display = new DisplayModel(config);
        this.listeners = Collections.synchronizedList(new ArrayList<>());
        this.parser = new EscapeParser(this);
        this.tabStops = new BitSet(config.cols() + 1);
        // Initialize default tab stops every 8 columns
        for (int c = 1; c <= config.cols(); c += 8) {
            tabStops.set(c);
        }
    }

    // --- Terminal interface ---

    @Override
    public void feed(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        for (byte b : data) {
            int val = b & 0xFF;
            routeByte(val);
        }
    }

    @Override
    public void feed(String text) {
        Objects.requireNonNull(text, "text must not be null");
        for (int i = 0; i < text.length(); i++) {
            routeByte(text.charAt(i));
        }
    }

    /**
     * Route a single byte to the appropriate handler.
     * ESC and BEL must reach the parser for escape sequence processing.
     * Other control characters are handled directly.
     * Printable characters go to the parser.
     */
    private void routeByte(int b) {
        if (b == ESC || b == BEL || b == 0) {
            parser.feed(b);
        } else if (b == 0x0E || b == 0x0F) {
            handleControl(b); // SO / SI
        } else if (b < 0x20 || b == 0x7F) {
            handleControl(b);
        } else if (b == 0x84 || b == 0x85) {
            handleControl(b);
        } else if (b >= 0x20 && b <= 0x7E) {
            parser.feed(b);
        }
        // bytes 0x80+ (except IND/RI) are silently ignored
    }

    /**
     * Handle a control character (0x00–0x1F except ESC/BEL, plus DEL, IND, RI, SO, SI).
     */
    protected void handleControl(int b) {
        switch (b) {
            case 0x00 -> {/* NUL — no effect */}
            case 0x08 -> display.cursor().back(1);                        // BS
            case 0x09 -> tabForward();                                    // HT
            case 0x0A, 0x0B, 0x0C -> lineFeed();                        // LF, VT, FF
            case 0x0D -> {                                                // CR
                display.cursor().setPos(display.cursor().row(), 1);
                display.screen().setWrapPending(false);
            }
            case 0x0E -> selectG0Charset();                               // SO — shift out
            case 0x0F -> selectG1Charset();                               // SI — shift in
            case 0x11 -> {/* DC1 — XON (flow control, no-op in emulator) */}
            case 0x12 -> {/* DC2 — group select (no-op) */}
            case 0x13 -> {/* DC3 — XOFF (flow control, no-op in emulator) */}
            case 0x14 -> {/* DC4 — restart (no-op) */}
            case 0x84 -> index();                                         // IND
            case 0x85 -> reverseIndex();                                  // RI
            case 0x7F -> {/* DEL — no effect */}
            default -> {/* Unknown control — ignore */}
        }
    }

    /** Advance to next tab stop (default every 8 columns). */
    private void tabForward() {
        Cursor cur = display.cursor();
        int current = cur.col();
        // Find next tab stop after current column
        int next = tabStops.nextSetBit(current + 1);
        if (next == -1 || next > config.cols()) {
            next = config.cols();
        }
        display.cursor().setPos(cur.row(), next);
    }

    /** Line feed (scroll if at bottom of scroll region). */
    private void lineFeed() {
        Cursor cur = display.cursor();
        int newRow = cur.row() + 1;
        if (newRow > display.screen().scrollBottom()) {
            display.scrollDown();
            display.cursor().setPos(display.screen().scrollBottom(), cur.col());
        } else {
            display.cursor().setPos(newRow, cur.col());
        }
    }

    /** Index (scroll region scrolls down, cursor stays at bottom if already there). */
    private void index() {
        Cursor cur = display.cursor();
        if (cur.row() < display.screen().scrollBottom()) {
            display.cursor().setPos(cur.row() + 1, cur.col());
        } else {
            display.scrollDown();
        }
    }

    /** Reverse index (scroll region scrolls up, cursor stays at top if already there). */
    private void reverseIndex() {
        Cursor cur = display.cursor();
        if (cur.row() > display.screen().scrollTop()) {
            display.cursor().setPos(cur.row() - 1, cur.col());
        } else {
            display.scrollUp();
        }
    }

    /** Set tab stop at current column (HTS). */
    protected void tabSet() {
        tabStops.set(display.cursor().col());
    }

    /** Clear tab stops. */
    protected void tabClear(int mode) {
        if (mode == 0 || mode == 3) {
            // Clear current tab stop
            tabStops.clear(display.cursor().col());
        } else if (mode == 2) {
            // Clear all tab stops
            tabStops.clear();
            // Re-initialize defaults
            for (int c = 1; c <= config.cols(); c += 8) {
                tabStops.set(c);
            }
        } else if (mode == 3) {
            tabStops.clear(display.cursor().col());
        }
    }

    // --- Character set hooks (for subclasses like VT500) ---

    /** Called on SO (0x0E) to select G0 character set. */
    protected void selectG0Charset() { /* Default: no-op */ }

    /** Called on SI (0x0F) to select G1 character set. */
    protected void selectG1Charset() { /* Default: no-op */ }

    // --- Event listeners ---

    @Override
    public void addEventListener(TerminalEventListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        listeners.add(listener);
    }

    @Override
    public void removeEventListener(TerminalEventListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void reset() {
        display.clear();
        display.setCurrentAttr(TermAttr.DEFAULT);
        display.setOriginMode(config.originMode());
        parser.reset();
        // Reset tab stops to defaults
        tabStops.clear();
        for (int c = 1; c <= config.cols(); c += 8) {
            tabStops.set(c);
        }
        onReset();
    }

    @Override
    public String title() { return display.title(); }

    @Override
    public boolean supportsColor() { return config.colorDepth() > 0; }

    // --- EscapeParser.SequenceHandler ---

    @Override
    public void handleChar(int codepoint) {
        display.putChar(codepoint);
    }

    @Override
    public void handleCSI(CSIParams params) {
        char finalByte = params.finalByte();

        switch (finalByte) {
            case 'A' -> display.cursorUp(params.get(0, 1));       // CUU
            case 'B' -> display.cursorDown(params.get(0, 1));     // CUD
            case 'C' -> display.cursorForward(params.get(0, 1));  // CUF
            case 'D' -> display.cursorBack(params.get(0, 1));     // CUB
            case 'E' -> cursorNextLine(params.get(0, 1));         // CNL
            case 'F' -> cursorPrevLine(params.get(0, 1));         // CPL
            case 'G' -> cursorHorizontalAbsolute(params.get(0, 1)); // CHA
            case 'H' -> onCsiCursorPosition(params);               // CUP
            case 'J' -> onCsiEraseDisplay(params);                 // ED
            case 'K' -> onCsiEraseLine(params);                    // EL
            case 'L' -> onCsiInsertLine(params);                   // IL
            case 'M' -> onCsiDeleteLine(params);                   // DL
            case 'P' -> onCsiDeleteChar(params);                   // DCH
            case '@' -> onCsiInsertChar(params);                   // ICH
            case 'X' -> onCsiEraseChar(params);                    // ECH
            case 'f' -> onCsiCursorPosition(params);               // HVP
            case 'd' -> cursorVerticalAbsolute(params.get(0, 1));  // VPA
            case 'T' -> onCsiTabClear(params);                     // TBC
            case 'N' -> onCsiSingleShift2(params);                 // SS2
            case 'O' -> onCsiSingleShift3(params);                 // SS3
            default -> {/* Unknown CSI — subclass may handle */}
        }
    }

    @Override
    public void handleEscSequence(char letter) {
        switch (letter) {
            case 'D' -> index();               // IND — index
            case 'E' -> {/* NEL — next line (CR + LF behavior) */
                display.cursor().setPos(display.cursor().row(), 1);
                lineFeed();
            }
            case 'H' -> tabSet();               // HTS — horizontal tab set
            case 'M' -> reverseIndex();         // RI — reverse index
            case '=' -> {/* DECKPAM — application keypad mode */}
            case '>' -> {/* DECKPNM — numeric keypad mode */}
            case '7' -> {/* DECSC — save cursor (handled by subclass) */}
            case '8' -> {/* DECRC — restore cursor (handled by subclass) */}
            case 'Z' -> {/* DECKPAM — back index key (handled by subclass) */}
            default -> {/* Unknown ESC sequence */}
        }
    }

    @Override
    public void handleEscHash(char digit) {
        // VT52/VT100-style ESC # n sequences
        switch (digit) {
            case '3' -> {/* DECSED — set reversed video mode */}
            case '8' -> {/* DECDBL — double-strike (bold) mode */}
            case '4' -> {/* DECSWL — single-width line (no-op) */}
            case '6' -> {/* DECDDC — double-height characters (not supported) */}
            default -> {/* Unknown ESC # n sequence */}
        }
    }

    @Override
    public void handleOSC(String data) {
        onOscTitle(data);
    }

    // --- Protected hooks for common CSI sequences ---

    protected void onCsiCursorPosition(CSIParams p) {
        int row = p.get(0, 1);
        int col = p.get(1, 1);
        display.cursorPosition(row, col);
    }

    protected void onCsiEraseDisplay(CSIParams p) {
        display.eraseDisplay(p.get(0, 0));
    }

    protected void onCsiEraseLine(CSIParams p) {
        display.eraseLine(p.get(0, 0));
    }

    protected void onCsiInsertLine(CSIParams p) {
        display.screen().insertLines(p.get(0, 1));
    }

    protected void onCsiDeleteLine(CSIParams p) {
        display.screen().deleteLines(p.get(0, 1));
    }

    protected void onCsiDeleteChar(CSIParams p) {
        display.screen().deleteChars(p.get(0, 1));
    }

    protected void onCsiInsertChar(CSIParams p) {
        display.screen().insertChars(p.get(0, 1));
    }

    protected void onCsiEraseChar(CSIParams p) {
        display.screen().eraseChars(p.get(0, 1));
    }

    protected void onCsiTabClear(CSIParams p) {
        tabClear(p.get(0, 0));
    }

    protected void onCsiSingleShift2(CSIParams p) { /* Default: no-op */ }
    protected void onCsiSingleShift3(CSIParams p) { /* Default: no-op */ }

    protected void cursorNextLine(int count) {
        Cursor cur = display.cursor();
        int newRow = Math.min(config.rows(), cur.row() + count);
        display.cursor().setPos(newRow, 1);
    }

    protected void cursorPrevLine(int count) {
        Cursor cur = display.cursor();
        int newRow = Math.max(1, cur.row() - count);
        display.cursor().setPos(newRow, 1);
    }

    protected void cursorHorizontalAbsolute(int col) {
        int c = Math.max(1, Math.min(col, config.cols()));
        display.cursor().setPos(display.cursor().row(), c);
    }

    protected void cursorVerticalAbsolute(int row) {
        int r = Math.max(1, Math.min(row, config.rows()));
        display.cursor().setPos(r, display.cursor().col());
    }

    protected void onOscTitle(String data) {
        if (data.startsWith("0;") || data.startsWith("2;")) {
            String title = data.substring(2);
            display.setTitle(title);
            for (TerminalEventListener l : listeners) {
                l.onTitleChange(title);
            }
        } else if (data.startsWith("1;")) {
            String icon = data.substring(2);
            display.setIconTitle(icon);
            for (TerminalEventListener l : listeners) {
                l.onIconTitleChange(icon);
            }
        }
    }

    /** Called on reset to allow subclass-specific state clearing. */
    protected void onReset() {
        outputBuffer.setLength(0);
    }

    /** Default render — returns visible lines from display model. */
    @Override
    public List<String> render() {
        return display.render();
    }

    // --- Display access ---

    /** Get the display model (protected for subclasses). */
    protected DisplayModel display() { return display; }

    /** Notify listeners of a title change. */
    protected void fireTitleChange(String title) {
        for (TerminalEventListener l : listeners) {
            l.onTitleChange(title);
        }
    }

    /** Get tab stops bit set (for testing). */
    BitSet tabStops() { return tabStops; }

    /**
     * Write data to the terminal output buffer.
     * Used by subclasses to generate responses (DSR, DECRQM) that
     * the transport layer can read and send to the peer.
     *
     * @param data the string to output
     */
    protected void output(String data) {
        outputBuffer.append(data);
    }

    /**
     * Read and clear the output buffer.
     *
     * @return the buffered output, or null if empty
     */
    public String readOutput() {
        if (outputBuffer.length() == 0) return null;
        String result = outputBuffer.toString();
        outputBuffer.setLength(0);
        return result;
    }

    /**
     * Check if there is buffered output available.
     */
    public boolean hasOutput() {
        return outputBuffer.length() > 0;
    }

    // --- Terminal interface delegation ---

    @Override
    public Cursor cursor() { return display.cursor(); }

    @Override
    public TermAttr currentAttr() { return display.currentAttr(); }

    @Override
    public TerminalConfig config() { return config; }

    @Override
    public DisplayModel displayModel() { return display; }
}
