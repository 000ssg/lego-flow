package ssg.legoflow.network.terminals.tn3270;

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
 * TN3270 terminal emulator.
 *
 * <p>Implements the IBM 3270 terminal protocol over TN3270 (RFC 1576) Telnet option.
 * The 3270 protocol is fundamentally different from VT-style terminals:
 * <ul>
 *   <li>Field-structured data instead of cursor-addressable cells</li>
 *   <li>32-byte keyboard input area for virtual key codes</li>
 *   <li>5-bit character encoding (3270 data stream)</li>
 *   <li>Read-only, read-write, bold, underline field attributes</li>
 *   <li>Control functions: PPI, RTS, TSS, ECD, UNDO, Flash, RK, ATN</li>
 * </ul>
 *
 * <p>The terminal processes input bytes as 3270 data stream records,
 * parsing field-length encoded data with attributes.
 *
 * <p>Supported configurations:
 * <ul>
 *   <li>24×80 (standard) — default</li>
 *   <li>24×132 (wide)</li>
 *   <li>43×80 (tall)</li>
 *   <li>43×132 (wide tall)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TN3270Terminal implements Terminal {

    /** Default 3270 screen size. */
    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLS = 80;

    public static TN3270Terminal create(TerminalConfig config) {
        return new TN3270Terminal(config);
    }

    public static TN3270Terminal create() {
        return create(TerminalConfig.builder()
            .rows(DEFAULT_ROWS).cols(DEFAULT_COLS).build());
    }

    // --- Auto-registration with TerminalFactory ---
    static {
        TerminalFactory.register("tn3270", config -> TN3270Terminal.create(config));
        TerminalFactory.register("3270", config -> TN3270Terminal.create(config));
    }

    private final TerminalConfig config;
    private final TN3270Screen screen;
    private final List<TerminalEventListener> listeners;
    private final TN3270DataStreamParser parser;
    private boolean useDataStream = false;


    private TN3270Terminal(TerminalConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.screen = new TN3270Screen(config);
        this.listeners = new ArrayList<>();
        this.parser = new TN3270DataStreamParser();

    }

    // --- Terminal interface ---

    @Override
    public void feed(byte[] data) {
        Objects.requireNonNull(data, "data must not be null");
        if (useDataStream && data.length > 0) {
            parseDataStream(data);
        } else {
            processRawBytes(data);
        }
    }

    @Override
    public void feed(String text) {
        Objects.requireNonNull(text, "text must not be null");
        if (useDataStream && text.length() > 0) {
            parseDataStream(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else {
            processRawString(text);
        }
    }

    /**
     * Process a 3270 data stream.
     */
    private void parseDataStream(byte[] data) {
        List<TN3270DataStreamParser.DataStreamRecord> records = parser.parse(data);
        screen.applyDataStream(records);
        fireDisplayUpdate();
    }

    /**
     * Process raw bytes directly onto the screen (simple mode).
     */
    private void processRawBytes(byte[] data) {
        for (byte b : data) {
            int val = b & 0xFF;
            if (val >= 0x20 && val <= 0x7E) {
                screen.writeBytes(new byte[]{b}, TN3270FieldAttr.NORMAL);
            } else if (val == 0x0A) {
                // LF — move to next line, column 1
                if (screen.cursorRow() < config.rows()) {
                    screen.cursorPosition(screen.cursorRow() + 1, 1);
                }
            } else if (val == 0x0D) {
                // CR — move to column 1
                screen.cursorPosition(screen.cursorRow(), 1);
            } else if (val == 0x08) {
                // BS — backspace
                int col = screen.cursorCol() - 1;
                if (col < 1) col = 1;
                screen.cursorPosition(screen.cursorRow(), col);
            }
            // Other control chars ignored
        }
        fireDisplayUpdate();
    }

    /**
     * Process raw string directly onto the screen (simple mode).
     */
    private void processRawString(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x20 && ch <= 0x7E) {
                screen.writeChars(new char[]{ch}, TN3270FieldAttr.NORMAL);
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
        return convertToTermAttr(screen.attrAt(screen.cursorRow(), screen.cursorCol()));
    }

    @Override
    public TerminalConfig config() {
        return config;
    }

    @Override
    public DisplayModel displayModel() {
        // Create a minimal DisplayModel wrapper around TN3270Screen
        return null; // TN3270 uses its own screen model
    }

    private TermAttr convertToTermAttr(TN3270FieldAttr attr) {
        TermAttr.Builder builder = TermAttr.builder();
        if (attr.isBold()) builder.bold(true);
        if (attr.isUnderlined()) builder.underline(TermAttr.UNDERLINE_SINGLE);
        if (attr.isReversed()) builder.reverse(true);
        return builder.build();
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
        return "tn3270";
    }

    @Override
    public String title() {
        return "TN3270";
    }

    @Override
    public boolean supportsColor() {
        return true; // TN3270 supports background colors
    }

    // --- TN3270-specific methods ---

    /**
     * Get the screen model.
     */
    public TN3270Screen screen() {
        return screen;
    }

    /**
     * Get the keyboard area data (32 bytes).
     */
    public byte[] keyboardArea() {
        return screen.getKeyboardArea();
    }

    /**
     * Set keyboard area data (for user input processing).
     */
    public void setKeyboardArea(byte[] data) {
        screen.setKeyboardArea(data);
    }

    /**
     * Move the cursor to a specific row and column (1-based).
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
     * Set the cursor position from a raw 3270 byte value.
     *
     * <p>3270 encodes cursor position as: row = byte - 64, col = byte - 64.
     * Valid range: 1–96.
     */
    public void cursorPositionFromByte(byte b) {
        int val = b & 0xFF;
        int row = val - 64;
        int col = row; // Both row and col use the same encoding
        if (row >= 1 && row <= config.rows()) {
            screen.cursorPosition(row, 1);
        }
    }

    /**
     * Erase the entire screen (ECD).
     */
    public void eraseAll() {
        screen.eraseAll();
        fireDisplayUpdate();
    }

    /**
     * Erase the field at the current cursor position.
     */
    public void eraseField() {
        int r = screen.cursorRow();
        int c = screen.cursorCol();
        screen.clearField(r, c, r, Math.min(c, config.cols()));
        fireDisplayUpdate();
    }

    /**
     * Check if the current cell is editable.
     */
    public boolean isEditable() {
        return screen.isEditable(screen.cursorRow(), screen.cursorCol());
    }

    /**
     * Write a string to the screen at the current cursor position.
     */
    public void writeString(String text) {
        feed(text);
    }

    /**
     * Enable or disable 3270 data stream mode.
     *
     * <p>When true (default), input is parsed as 3270 data stream.
     * When false, input is processed as raw characters.
     */
    public void setUseDataStream(boolean useDataStream) {
        this.useDataStream = useDataStream;
    }

    /**
     * Check if data stream mode is enabled.
     */
    public boolean isUseDataStream() {
        return useDataStream;
    }
}
