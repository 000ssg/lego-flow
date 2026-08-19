package ssg.legoflow.network.terminals.tn5250;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.base.display.Cursor;
import ssg.legoflow.network.terminals.base.display.DisplayModel;
import ssg.legoflow.network.terminals.base.display.TermAttr;
import ssg.legoflow.network.terminals.base.event.TerminalEvent;
import ssg.legoflow.network.terminals.base.event.TerminalEventListener;
import ssg.legoflow.network.terminals.base.io.Terminal;
import ssg.legoflow.network.terminals.base.io.TerminalFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TN5250 terminal emulator.
 *
 * <p>Implements the IBM 5250 terminal protocol over TN5250 (RFC 1662) Telnet option.
 * The 5250 protocol was designed for IBM System/38 and later AS/400 (IBM i) systems.
 *
 * <p>Key features:
 * <ul>
 *   <li>Field-oriented screen model (24×80 default)</li>
 *   <li>Field attributes: emphasis, automatic skip, blank/protected</li>
 *   <li>5250 data stream with field-length encoded records</li>
 *   <li>Keyboard area (32 bytes) for virtual key codes</li>
 *   <li>Full screen clear, field erase, and cursor positioning</li>
 * </ul>
 *
 * <p>Supported screen sizes:
 * <ul>
 *   <li>24×80 (standard) — default</li>
 *   <li>52×80 (lettermode)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TN5250Terminal implements Terminal {

    /** Default 5250 screen size. */
    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLS = 80;

    public static TN5250Terminal create(TerminalConfig config) {
        return new TN5250Terminal(config);
    }

    public static TN5250Terminal create() {
        return create(TerminalConfig.builder()
            .rows(DEFAULT_ROWS).cols(DEFAULT_COLS).build());
    }

    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("tn5250", config -> TN5250Terminal.create(config));
        TerminalFactory.register("5250", config -> TN5250Terminal.create(config));
    }

    private final TerminalConfig config;
    private final TN5250Screen screen;
    private final List<TerminalEventListener> listeners;

    private TN5250Terminal(TerminalConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.screen = new TN5250Screen(config);
        this.listeners = new ArrayList<>();
    }

    // --- Terminal interface ---

    @Override
    public void feed(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        for (byte b : data) {
            int val = b & 0xFF;
            if (val >= 0x20 && val <= 0x7E) {
                screen.writeBytes(new byte[]{b}, TN5250FieldAttr.NORMAL);
            } else if (val == 0x0A) {
                if (screen.cursorRow() < config.rows()) {
                    screen.cursorPosition(screen.cursorRow() + 1, 1);
                }
            } else if (val == 0x0D) {
                screen.cursorPosition(screen.cursorRow(), 1);
            } else if (val == 0x08) {
                int col = screen.cursorCol() - 1;
                if (col < 1) col = 1;
                screen.cursorPosition(screen.cursorRow(), col);
            }
        }
        fireDisplayUpdate();
    }

    @Override
    public void feed(String text) {
        Objects.requireNonNull(text, "text must not be null");
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x20 && ch <= 0x7E) {
                screen.writeChars(new char[]{ch}, TN5250FieldAttr.NORMAL);
            } else if (ch == '\n') {
                if (screen.cursorRow() < config.rows()) {
                    screen.cursorPosition(screen.cursorRow() + 1, 1);
                }
            } else if (ch == '\r') {
                screen.cursorPosition(screen.cursorRow(), 1);
            } else if (ch == '\b') {
                int col = screen.cursorCol() - 1;
                if (col < 1) col = 1;
                screen.cursorPosition(screen.cursorRow(), col);
            }
        }
        fireDisplayUpdate();
    }

    private void fireDisplayUpdate() {
        // TN3270/TN5250 use their own screen model; no event broadcast needed
    }

    @Override
    public List<String> render() {
        return screen.render();
    }

    @Override
    public Cursor cursor() {
        return new Cursor(screen.cursorRow(), screen.cursorCol());
    }

    @Override
    public TermAttr currentAttr() {
        TN5250FieldAttr attr = screen.cursorAttr();
        TermAttr.Builder builder = TermAttr.builder();
        if (attr.isEmphasized()) builder.bold(true);
        return builder.build();
    }

    @Override
    public TerminalConfig config() {
        return config;
    }

    @Override
    public DisplayModel displayModel() {
        return null; // TN5250 uses its own screen model
    }

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
        screen.reset();
        fireDisplayUpdate();
    }

    @Override
    public String type() {
        return "tn5250";
    }

    @Override
    public String title() {
        return "TN5250";
    }

    @Override
    public boolean supportsColor() {
        return true; // 5250 supports background colors on color displays
    }

    // --- TN5250-specific methods ---

    /**
     * Get the screen model.
     */
    public TN5250Screen screen() {
        return screen;
    }

    /**
     * Get the keyboard area.
     */
    public byte[] keyboardArea() {
        return screen.getKeyboardArea();
    }

    /**
     * Set keyboard area.
     */
    public void setKeyboardArea(byte[] data) {
        screen.setKeyboardArea(data);
    }

    /**
     * Move the cursor to a specific position (1-based).
     */
    public void cursorPosition(int row, int col) {
        screen.cursorPosition(row, col);
    }

    /**
     * Get the current cursor row.
     */
    public int cursorRow() {
        return screen.cursorRow();
    }

    /**
     * Get the current cursor column.
     */
    public int cursorCol() {
        return screen.cursorCol();
    }

    /**
     * Erase the screen.
     */
    public void erase() {
        screen.erase();
        fireDisplayUpdate();
    }

    /**
     * Check if the current cell is editable.
     */
    public boolean isEditable() {
        return screen.isEditable(screen.cursorRow(), screen.cursorCol());
    }

    /**
     * Write characters with a specific attribute.
     */
    public void writeChars(char[] chars, TN5250FieldAttr attr) {
        screen.writeChars(chars, attr);
        fireDisplayUpdate();
    }

    /**
     * Write a string to the screen at the current cursor position.
     */
    public void writeString(String text) {
        feed(text);
    }

    /**
     * Set the field attribute for a rectangular area.
     */
    public void setFieldAttrs(int startRow, int startCol, int endRow, int endCol, TN5250FieldAttr attr) {
        screen.setFieldAttrs(startRow, startCol, endRow, endCol, attr);
    }
}
