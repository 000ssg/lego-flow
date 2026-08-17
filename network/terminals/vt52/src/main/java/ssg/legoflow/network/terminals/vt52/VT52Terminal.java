package ssg.legoflow.network.terminals.vt52;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Character;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.Screen;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;

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
 * <p>Supported commands:
 * <ul>
 *   <li>ESC I — cursor right</li>
 *   <li>ESC F — cursor left</li>
 *   <li>ESC S — cursor up</li>
 *   <li>ESC R — cursor down</li>
 *   <li>ESC E — clear to end of line</li>
 *   <li>ESC D — line feed (scroll if at bottom)</li>
 *   <li>ESC J — clear display</li>
 *   <li>ESC Y row col — cursor address</li>
 *   <li>ESC = — application keypad</li>
 *   <li>ESC &gt; — numeric keypad</li>
 *   <li>ESC &lt; — normal keypad</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class VT52Terminal implements Terminal {

    public static VT52Terminal create(TerminalConfig config) {
        return new VT52Terminal(config);
    }

    private final TerminalConfig config;
    private final DisplayModel display;
    private final List<TerminalEventListener> listeners;
    private int state = STATE_DATA;
    private String escapeCommand = null;

    private static final int STATE_DATA = 0;
    private static final int STATE_ESCAPE = 1;
    private static final int STATE_Y_ADDRESS = 2;

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
            case STATE_DATA:
                if (b == 0x1B) {
                    state = STATE_ESCAPE;
                } else if (b == '\r') {
                    // CR — move to column 1
                    display.cursor().setPos(display.cursor().row(), 1);
                } else if (b == '\n') {
                    // LF — line feed
                    lineFeed();
                } else if (b == '\b') {
                    // Backspace
                    display.cursor().back(1);
                } else if (b == '\t') {
                    // Tab — advance to next tab stop
                    display.cursorForward(8 - (display.cursor().col() - 1) % 8);
                } else {
                    display.putChar(b);
                }
                break;

            case STATE_ESCAPE:
                handleEscape(b);
                break;

            case STATE_Y_ADDRESS:
                handleYAddress(b);
                break;
        }
    }

    private void handleEscape(int b) {
        char c = (char) b;
        switch (c) {
            case 'I' -> display.cursorForward(1);
            case 'F' -> display.cursor().back(1);
            case 'S' -> display.cursorUp(1);
            case 'R' -> display.cursorDown(1);
            case 'E' -> {
                // Clear from cursor to end of line
                display.eraseLine(0);
            }
            case 'D' -> lineFeed();
            case 'J' -> {
                display.clear();
            }
            case 'Y' -> {
                state = STATE_Y_ADDRESS;
                escapeCommand = "Y";
            }
            case '=' -> {/* Application keypad */}
            case '>' -> {/* Numeric keypad */}
            case '<' -> {/* Normal keypad */}
            default -> state = STATE_DATA;
        }
        if (state != STATE_Y_ADDRESS) state = STATE_DATA;
    }

    private void handleYAddress(int b) {
        if (escapeCommand.equals("Y")) {
            // Y parameter received (row)
            int row = b - 32; // VT52 encodes as value+32
            escapeCommand = String.valueOf(row);
        } else {
            // X parameter received (col)
            int col = b - 32;
            int row = Integer.parseInt(escapeCommand);
            display.cursorPosition(row, col);
            state = STATE_DATA;
            escapeCommand = null;
        }
    }

    private void lineFeed() {
        int newRow = display.cursor().row() + 1;
        if (newRow > config.rows()) {
            display.scrollDown();
            display.cursor().setPos(config.rows(), display.cursor().col());
        } else {
            display.cursor().setPos(newRow, display.cursor().col());
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
        escapeCommand = null;
    }

    @Override
    public String type() { return "vt52"; }

    @Override
    public String title() { return display.title(); }

    @Override
    public boolean supportsColor() { return false; }
}
