package ssg.legoflow.network.terminals.tn3270;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import ssg.legoflow.network.terminals.tn3270.TN3270DataStreamParser.KeyboardDataRecord;
import ssg.legoflow.network.terminals.tn3270.TN3270DataStreamParser.DataStreamRecord;
import ssg.legoflow.network.terminals.tn3270.TN3270DataStreamParser.FieldDataRecord;
import java.util.ArrayList;
import java.util.List;
/**
 * 3270 screen model.
 *
 * <p>The 3270 screen is fundamentally different from VT-style terminals.
 * Instead of cursor-addressable cells, 3270 uses field-structured data:
 * <ul>
 *   <li>Keyboard area: first 32 bytes (user input buffer)</li>
 *   <li>Screen data: structured fields with attributes</li>
 * </ul>
 *
 * <p>The screen data area supports standard 3270 sizes:
 * <ul>
 *   <li>24×80 (standard)</li>
 *   <li>24×132 (wide)</li>
 *   <li>43×80 (tall)</li>
 *   <li>43×132 (wide tall)</li>
 *   <li>52×80 (lettermode)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TN3270Screen {

    /** 3270 character encoding table (5-bit → ASCII).
     *
     * <p>The 3270 data stream uses 5-bit characters (values 0–31) which map
     * to ASCII via a specific encoding table. Values 0x80–0xBF are encoded
     * in the data stream with the high bit set.
     */
    private static final char[] CHARSET3270 = {
        // 0-15
        0,    0,    0,    0,    0,    0,    0,    0,    // 0-7 (NUL-SOH)
        0,    0,    0,    0,    0,    0,    0,    0,    // 8-15
        // 16-31
        0x40, // 16 → @
        0x80, // 17 → ×
        0x6E, // 18 → n (lowercase e)
        0x7B, // 19 → { (open bracket)
        0x60, // 20 → ` (backtick)
        0xFF, // 21 → ÿ
        0x5B, // 22 → [
        0x7D, // 23 → } (close bracket)
        0x49, // 24 → I (capital i with dotless)
        0x5F, // 25 → _
        0x69, // 26 → i (lowercase)
        0xFF, // 27 → ÿ
        0x7F, // 28 → delete
        0x6A, // 29 → j (lowercase)
        0x3C, // 30 → <
        0x43, // 31 → C (C with cedilla)
    };

    private static final char SPACE = ' ';

    private final int rows;
    private final int cols;
    private final byte[][] screen;      // raw character bytes (5-bit encoded)
    private final TN3270FieldAttr[][] attrs; // field attributes per cell
    private byte[] keyboardArea;  // 32-byte keyboard input area
    private int cursorRow = 1;
    private int cursorCol = 1;

    /**
     * Create a new 3270 screen.
     *
     * @param config the terminal configuration
     */
    public TN3270Screen(TerminalConfig config) {
        this.rows = config.rows();
        this.cols = config.cols();
        this.screen = new byte[rows + 1][cols + 1]; // 1-based indexing
        this.attrs = new TN3270FieldAttr[rows + 1][cols + 1];
        this.keyboardArea = new byte[32];
        reset();
    }

    /**
     * Reset the screen to initial state.
     */
    public void reset() {
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                screen[r][c] = SPACE;
                attrs[r][c] = TN3270FieldAttr.NORMAL;
            }
        }
        keyboardArea = new byte[32];
        cursorRow = 1;
        cursorCol = 1;
    }

    /**
     * Erase the entire screen (ECD — Erase Change Detection).
     */
    public void eraseAll() {
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                screen[r][c] = SPACE;
                attrs[r][c] = TN3270FieldAttr.NORMAL;
            }
        }
    }

    /**
     * Erase all unchanged fields (UNDO — Erase All Unchanged).
     */
    public void eraseUnchanged() {
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                if (attrs[r][c].isProtected()) {
                    // Leave protected fields unchanged
                    screen[r][c] = SPACE;
                } else {
                    screen[r][c] = SPACE;
                    attrs[r][c] = TN3270FieldAttr.NORMAL;
                }
            }
        }
    }

    /**
     * Apply a 3270 data stream to the screen.
     *
     * @param records the parsed data stream records
     */
    public void applyDataStream(List<DataStreamRecord> records) {
        for (DataStreamRecord record : records) {
            applyRecord(record);
        }
    }

    private void applyRecord(DataStreamRecord record) {
        switch (record.kind()) {
            case 0 -> applyKeyboardData((KeyboardDataRecord) record);
            case 1 -> applyFieldData((FieldDataRecord) record);
            case 10 -> {/* PPI — end of stream, no action needed */}
            case 11 -> applyRTS();
            case 12 -> applyTSS();
            case 13 -> applyECD();
            case 14 -> applyUNDO();
            case 15 -> applyFlash();
            case 16 -> applyRK();
            case 17 -> applyATN();
            case -1 -> {/* Unknown control — ignore */}
            default -> {/* Ignore unknown */}
        }
    }

    private void applyKeyboardData(KeyboardDataRecord record) {
        byte[] data = record.data();
        int copyLen = Math.min(data.length, keyboardArea.length);
        System.arraycopy(data, 0, keyboardArea, 0, copyLen);
    }

    private void applyFieldData(FieldDataRecord record) {
        byte[] data = record.data();
        TN3270FieldAttr attr = record.attr();

        // Apply field data to the screen starting at cursor position
        for (int i = 0; i < data.length && cursorRow <= rows; i++) {
            int col = cursorCol;
            if (col > cols) {
                cursorRow++;
                cursorCol = 1;
                col = cursorCol;
                if (cursorRow > rows) break;
            }
            screen[cursorRow][col] = (byte) tn3270Char(data[i]);
            attrs[cursorRow][col] = attr;
            cursorCol++;
        }
    }

    private void applyRTS() {
        // Reset to Screen: clear entire screen
        eraseAll();
    }

    private void applyTSS() {
        // Top of Screen Screen: clear screen and move cursor to (1,1)
        eraseAll();
        cursorRow = 1;
        cursorCol = 1;
    }

    private void applyECD() {
        // Erase Change Detection: clear screen
        eraseAll();
    }

    private void applyUNDO() {
        eraseUnchanged();
    }

    private void applyFlash() {
        // Flash screen: mark all as flashing
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                if (attrs[r][c].isEditable()) {
                    attrs[r][c] = new TN3270FieldAttr(
                        attrs[r][c].primary() | 0x20,
                        attrs[r][c].secondary(),
                        attrs[r][c].label()
                    );
                }
            }
        }
    }

    private void applyRK() {
        // Request Keyboard: move cursor to first editable cell
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                if (attrs[r][c].isEditable()) {
                    cursorRow = r;
                    cursorCol = c;
                    return;
                }
            }
        }
    }

    private void applyATN() {
        // Attention: signal raised (no screen action needed)
    }

    /**
     * Write raw 3270 characters to the screen at the current cursor position.
     *
     * @param chars the 3270 encoded characters
     * @param attr  the field attribute to apply
     */
    public void writeChars(char[] chars, TN3270FieldAttr attr) {
        for (char ch : chars) {
            if (cursorRow <= rows) {
                int col = cursorCol;
                if (col > cols) {
                    cursorRow++;
                    cursorCol = 1;
                    col = 1;
                    if (cursorRow > rows) { cursorRow = rows; break; }
                }
                screen[cursorRow][col] = tn3270Byte(ch);
                attrs[cursorRow][col] = attr;
                cursorCol++;
            }
        }
    }

    /**
     * Write raw 3270 characters to the screen.
     *
     * @param bytes the 3270 encoded byte array
     * @param attr  the field attribute to apply
     */
    public void writeBytes(byte[] bytes, TN3270FieldAttr attr) {
        for (byte b : bytes) {
            if (cursorRow <= rows) {
                int col = cursorCol;
                if (col > cols) {
                    cursorRow++;
                    cursorCol = 1;
                    col = 1;
                    if (cursorRow > rows) { cursorRow = rows; break; }
                }
                screen[cursorRow][col] = b;
                attrs[cursorRow][col] = attr;
                cursorCol++;
            }
        }
    }

    /**
     * Decode a 3270 encoded byte to its character value.
     *
     * <p>3270 uses a 5-bit character set with the high bit.
     */
    private char tn3270Char(byte b) {
        int val = b & 0xFF;
        if (val < 0x80) {
            return (char) val; // ASCII character
        }
        // 5-bit character: lower 5 bits
        int idx = val & 0x1F;
        if (idx < CHARSET3270.length) {
            return CHARSET3270[idx];
        }
        return SPACE;
    }

    /**
     * Encode a character to its 3270 byte representation.
     */
    private byte tn3270Byte(char ch) {
        int val = ch & 0xFF;
        if (val >= 0x20 && val <= 0x7E) {
            return (byte) val;
        }
        // Find in charset
        for (int i = 0; i < CHARSET3270.length; i++) {
            if (CHARSET3270[i] == ch) {
                return (byte) (0x80 | i);
            }
        }
        return SPACE;
    }

    /**
     * Move the cursor to a specific position (1-based).
     */
    public void cursorPosition(int row, int col) {
        cursorRow = Math.max(1, Math.min(row, rows));
        cursorCol = Math.max(1, Math.min(col, cols));
    }

    /**
     * Set all cells in a field area to read-only.
     */
    public void setFieldReadOnly(int startRow, int startCol, int endRow, int endCol) {
        for (int r = startRow; r <= endRow; r++) {
            for (int c = startCol; c <= endCol; c++) {
                attrs[r][c] = TN3270FieldAttr.READ_ONLY;
            }
        }
    }

    /**
     * Set all cells in a field area to a specific attribute.
     */
    public void setFieldAttrs(int startRow, int startCol, int endRow, int endCol, TN3270FieldAttr attr) {
        for (int r = Math.max(1, startRow); r <= Math.min(rows, endRow); r++) {
            for (int c = Math.max(1, startCol); c <= Math.min(cols, endCol); c++) {
                attrs[r][c] = attr;
            }
        }
    }

    /**
     * Clear a field area (set to space with normal attributes).
     */
    public void clearField(int startRow, int startCol, int endRow, int endCol) {
        for (int r = Math.max(1, startRow); r <= Math.min(rows, endRow); r++) {
            for (int c = Math.max(1, startCol); c <= Math.min(cols, endCol); c++) {
                screen[r][c] = SPACE;
                attrs[r][c] = TN3270FieldAttr.NORMAL;
            }
        }
    }

    // --- Getters ---

    /**
     * Get the screen character at position (1-based).
     */
    public char charAt(int row, int col) {
        int val = screen[row][col] & 0xFF;
        if (val >= 0x80) {
            return tn3270Char(screen[row][col]);
        }
        return (char) val;
    }

    /**
     * Get the field attribute at position.
     */
    public TN3270FieldAttr attrAt(int row, int col) {
        return attrs[row][col];
    }

    /**
     * Get the full screen grid for rendering.
     */
    public char[][] getGrid() {
        char[][] grid = new char[rows + 1][];
        for (int r = 1; r <= rows; r++) {
            grid[r] = new char[cols + 1];
            for (int c = 1; c <= cols; c++) {
                grid[r][c] = charAt(r, c);
            }
        }
        return grid;
    }

    /**
     * Get the full attribute grid.
     */
    public TN3270FieldAttr[][] getAttrGrid() {
        TN3270FieldAttr[][] grid = new TN3270FieldAttr[rows + 1][];
        for (int r = 1; r <= rows; r++) {
            grid[r] = new TN3270FieldAttr[cols + 1];
            System.arraycopy(attrs[r], 0, grid[r], 0, cols + 1);
        }
        return grid;
    }

    /**
     * Get the keyboard area contents.
     */
    public byte[] getKeyboardArea() {
        return keyboardArea.clone();
    }

    /**
     * Set keyboard area data (for user input processing).
     */
    public void setKeyboardArea(byte[] data) {
        System.arraycopy(data, 0, keyboardArea, 0, Math.min(data.length, 32));
    }

    /**
     * Get the current cursor row.
     */
    public int cursorRow() { return cursorRow; }

    /**
     * Get the current cursor column.
     */
    public int cursorCol() { return cursorCol; }

    /**
     * Get the number of rows.
     */
    public int rows() { return rows; }

    /**
     * Get the number of columns.
     */
    public int cols() { return cols; }

    /**
     * Render the screen as a list of lines.
     */
    public List<String> render() {
        List<String> lines = new ArrayList<>();
        for (int r = 1; r <= rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 1; c <= cols; c++) {
                sb.append(charAt(r, c));
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    /**
     * Get the rendered screen as a single string with line breaks.
     */
    public String renderToString() {
        return String.join("\n", render());
    }

    /**
     * Check if the field at position is editable.
     */
    public boolean isEditable(int row, int col) {
        if (row >= 1 && row <= rows && col >= 1 && col <= cols) {
            return attrs[row][col].isEditable();
        }
        return false;
    }
}
