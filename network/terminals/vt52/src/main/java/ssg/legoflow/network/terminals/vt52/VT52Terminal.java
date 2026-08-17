package ssg.legoflow.network.terminals.vt52;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Character;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * VT52 terminal emulator.
 *
 * <p>The VT52 is the simplest DEC terminal, using an entirely different
 * command set from VT100. Commands are ESC followed by a single letter.
 * Cursor addressing uses printable ASCII characters (row/col encoded as
 * value + 32, so row 1 = '@').
 *
 * <p>Unlike later DEC terminals, the VT52 has no SGR support.
 * Visual attributes (reverse video, bold) are set via ESC # n sequences
 * and affect all subsequent character output until changed.
 *
 * <p>Supported commands:
 * <ul>
 *   <li>ESC I — cursor right</li>
 *   <li>ESC F — cursor left</li>
 *   <li>ESC S — cursor up</li>
 *   <li>ESC R — cursor down</li>
 *   <li>ESC E — clear to end of line</li>
 *   <li>ESC D — line feed (LF + CR, scrolls at bottom)</li>
 *   <li>ESC J — clear screen + home cursor</li>
 *   <li>ESC K — clear from cursor to end of screen</li>
 *   <li>ESC U — reverse line feed</li>
 *   <li>ESC = — application keypad</li>
 *   <li>ESC &gt; — numeric keypad</li>
 *   <li>ESC &lt; — normal keypad</li>
 *   <li>ESC Y row col — cursor address (value + 32 encoding)</li>
 *   <li>ESC # 3 — reversed video (affects subsequent output)</li>
 *   <li>ESC # 8 — bold (affects subsequent output)</li>
 * </ul>
 *
 * <p>LF (0x0A) performs line feed with carriage return, as per VT52 behavior.
 *
 * @since 0.2.0
 */
public final class VT52Terminal implements Terminal {

    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("vt52", config -> VT52Terminal.create(config));
    }


    public static VT52Terminal create(TerminalConfig config) {
        return new VT52Terminal(config);
    }

    private static final int STATE_DATA = 0;
    private static final int STATE_ESCAPE = 1;
    private static final int STATE_Y_ADDRESS = 2;
    private static final int STATE_HASH = 3;

    private final TerminalConfig config;
    private final DisplayModel display;
    private final List<TerminalEventListener> listeners;
    /** Buffer for terminal-generated output. */
    private final StringBuilder outputBuffer = new StringBuilder();
    private int state = STATE_DATA;
    private int yParam;
    private boolean reverseVideo;
    private boolean bold;

    private VT52Terminal(TerminalConfig config) {
        this.config = Objects.requireNonNull(config);
        this.display = new DisplayModel(config);
        this.listeners = Collections.synchronizedList(new ArrayList<>());
    }

    @Override
    public void feed(byte[] data) {
        Objects.requireNonNull(data);
        for (byte b : data) {
            process(b & 0xFF);
        }
    }

    @Override
    public void feed(String text) {
        Objects.requireNonNull(text);
        for (int i = 0; i < text.length(); i++) {
            process(text.charAt(i));
        }
    }

    private void process(int b) {
        switch (state) {
            case STATE_DATA -> handleData(b);
            case STATE_ESCAPE -> handleEscape(b);
            case STATE_Y_ADDRESS -> handleYAddress(b);
            case STATE_HASH -> handleHash(b);
        }
    }

    private void handleData(int b) {
        if (b == 0x1B) {
            state = STATE_ESCAPE;
        } else if (b == '\r') {
            // CR — move to column 1
            display.cursor().setPos(display.cursor().row(), 1);
        } else if (b == '\n') {
            // LF — line feed WITH carriage return (VT52-specific behavior)
            lineFeedAndHome();
        } else if (b == '\b') {
            // Backspace
            display.cursor().back(1);
        } else if (b == '\t') {
            // Tab — advance to next tab stop (every 8 columns)
            display.cursorForward(8 - (display.cursor().col() - 1) % 8);
        } else if (b >= 0x20 && b <= 0x7E) {
            // Printable character — apply visual state then output
            applyVisualState();
            display.putChar(b);
        }
        // Other control characters silently ignored (VT52 behavior)
    }

    /**
     * Apply VT52 visual state (reverseVideo, bold) to display attributes.
     * VT52 has no SGR; these flags affect all subsequent character output.
     */
    private void applyVisualState() {
        TermAttr current = display.currentAttr();
        boolean reverseChanged = current.reverse() != reverseVideo;
        boolean boldChanged = current.bold() != bold;
        if (reverseChanged || boldChanged) {
            TermAttr.Builder builder = current.toBuilder();
            if (reverseChanged) builder.reverse(reverseVideo);
            if (boldChanged) builder.bold(bold);
            display.setCurrentAttr(builder.build());
        }
    }

    /** Line feed with carriage return (VT52 LF/ESC D behavior). */
    private void lineFeedAndHome() {
        Cursor cur = display.cursor();
        int newRow = cur.row() + 1;
        if (newRow > config.rows()) {
            display.scrollDown();
            display.cursor().setPos(config.rows(), 1);
        } else {
            display.cursor().setPos(newRow, 1);
        }
    }

    private void handleEscape(int b) {
        char c = (char) b;
        switch (c) {
            case 'I' -> display.cursorForward(1);      // Cursor right
            case 'F' -> display.cursor().back(1);      // Cursor left
            case 'S' -> display.cursorUp(1);           // Cursor up
            case 'R' -> display.cursorDown(1);         // Cursor down
            case 'E' -> clearToEndOfLine();            // Clear to end of line
            case 'D' -> lineFeedAndHome();             // Line feed (LF + CR)
            case 'J' -> {
                clearScreen();
                display.cursor().setPos(1, 1);         // Clear screen + home
            }
            case 'K' -> clearToEndOfScreen();          // Clear from cursor to end of screen
            case 'U' -> reverseLineFeed();             // Reverse line feed
            case '=' -> {/* Application keypad */}
            case '>' -> {/* Numeric keypad */}
            case '<' -> {/* Normal keypad */}
            case 'Y' -> {
                state = STATE_Y_ADDRESS;
                yParam = 0;
            }
            case '#' -> {
                state = STATE_HASH;
            }
            default -> {/* Unknown — ignore */}
        }
        // STATE_Y_ADDRESS and STATE_HASH remain active; all others return to DATA
        if (state != STATE_Y_ADDRESS && state != STATE_HASH) {
            state = STATE_DATA;
        }
    }

    private void handleYAddress(int b) {
        if (yParam == 0) {
            // First byte: Y (row) parameter
            yParam = b - 32; // VT52 encodes as value+32
        } else {
            // Second byte: X (col) parameter
            int col = b - 32;
            display.cursorPosition(yParam, col);
            yParam = 0;
            state = STATE_DATA;
        }
    }

    private void handleHash(int b) {
        char c = (char) b;
        switch (c) {
            case '3' -> reverseVideo = true;  // Reversed video
            case '8' -> bold = true;           // Bold
            case '4' -> {/* Single-width (no-op) */}
            case '6' -> {/* Double-height (not supported) */}
            default -> {/* Unknown — ignore */}
        }
        state = STATE_DATA;
    }

    /** ESC E — clear to end of line. */
    private void clearToEndOfLine() {
        applyVisualState();
        TermAttr attr = display.currentAttr();
        Character space = new Character(' ', attr);
        Cursor cur = display.cursor();
        int r = cur.row() - 1;
        for (int c = cur.col() - 1; c < config.cols(); c++) {
            display.screen().getGrid()[r][c] = space;
        }
    }

    /** ESC K — clear from cursor to end of screen. */
    private void clearToEndOfScreen() {
        applyVisualState();
        TermAttr attr = display.currentAttr();
        Character space = new Character(' ', attr);
        Cursor cur = display.cursor();
        for (int r = cur.row(); r <= config.rows(); r++) {
            int fromCol = (r == cur.row()) ? cur.col() - 1 : 0;
            for (int c = fromCol; c < config.cols(); c++) {
                display.screen().getGrid()[r - 1][c] = space;
            }
        }
    }

    /** ESC J — clear entire screen. */
    private void clearScreen() {
        for (int r = 0; r < config.rows(); r++) {
            for (int c = 0; c < config.cols(); c++) {
                display.screen().getGrid()[r][c] = Character.EMPTY;
            }
        }
    }

    /** ESC U — reverse line feed (moves cursor up, scrolls at top). */
    private void reverseLineFeed() {
        Cursor cur = display.cursor();
        if (cur.row() > 1) {
            display.cursor().setPos(cur.row() - 1, cur.col());
        } else {
            display.scrollUp();
        }
    }

    @Override
    public List<String> render() { return display.render(); }

    @Override
    public Cursor cursor() { return display.cursor(); }

    @Override
    public TermAttr currentAttr() { return display.currentAttr(); }

    @Override
    public TerminalConfig config() { return config; }

    @Override
    public DisplayModel displayModel() { return display; }

    @Override
    public void addEventListener(TerminalEventListener listener) {
        Objects.requireNonNull(listener);
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
        state = STATE_DATA;
        yParam = 0;
        reverseVideo = false;
        bold = false;
        outputBuffer.setLength(0);
    }

    @Override
    public String type() { return "vt52"; }

    @Override
    public String title() { return display.title(); }

    @Override
    public boolean supportsColor() { return false; }

    /** Check if reverse video mode is active. */
    public boolean isReverseVideo() { return reverseVideo; }

    /** Check if bold mode is active. */
    public boolean isBold() { return bold; }

    /**
     * Write data to the terminal output buffer.
     */
    protected void output(String data) {
        outputBuffer.append(data);
    }

    /**
     * Read and clear the output buffer.
     */
    public String readOutput() {
        if (outputBuffer.length() == 0) return null;
        String result = outputBuffer.toString();
        outputBuffer.setLength(0);
        return result;
    }

    /** Check if there is buffered output available. */
    public boolean hasOutput() { return outputBuffer.length() > 0; }
}
