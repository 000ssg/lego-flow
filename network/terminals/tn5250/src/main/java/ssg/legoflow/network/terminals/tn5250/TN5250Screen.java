package ssg.legoflow.network.terminals.tn5250;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import java.util.ArrayList;
import java.util.List;
/**
 * TN5250 (5250) screen model.
 *
 * <p>The 5250 screen is field-oriented, like 3270, but with different
 * attribute encoding. Standard 5250 screen sizes are 24×80 or 52×80.
 *
 * <p>Each field has an attribute byte encoding emphasis, auto-skip, and blank.
 * The screen maintains a 2D grid of characters and field attributes.
 *
 * @since 0.2.0
 */
public final class TN5250Screen {

    private final int rows;
    private final int cols;
    private final char[][] screen;
    private final TN5250FieldAttr[][] attrs;
    private int cursorRow = 1;
    private int cursorCol = 1;
    private byte[] keyboardArea;

    /**
     * Create a new 5250 screen.
     */
    public TN5250Screen(TerminalConfig config) {
        this.rows = config.rows();
        this.cols = config.cols();
        this.screen = new char[rows + 1][cols + 1];
        this.attrs = new TN5250FieldAttr[rows + 1][cols + 1];
        this.keyboardArea = new byte[32];
        reset();
    }

    /**
     * Reset the screen to initial state.
     */
    public void reset() {
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                screen[r][c] = ' ';
                attrs[r][c] = TN5250FieldAttr.NORMAL;
            }
        }
        keyboardArea = new byte[32];
        cursorRow = 1;
        cursorCol = 1;
    }

    /**
     * Erase the screen (clear all data).
     */
    public void erase() {
        for (int r = 1; r <= rows; r++) {
            for (int c = 1; c <= cols; c++) {
                screen[r][c] = ' ';
                attrs[r][c] = TN5250FieldAttr.NORMAL;
            }
        }
    }

    /**
     * Write characters to the screen at the current cursor position.
     */
    public void writeChars(char[] chars, TN5250FieldAttr attr) {
        for (char ch : chars) {
            if (cursorRow <= rows) {
                int col = cursorCol;
                if (col > cols) {
                    cursorRow++;
                    cursorCol = 1;
                    col = 1;
                    if (cursorRow > rows) { cursorRow = rows; break; }
                }
                screen[cursorRow][col] = ch;
                attrs[cursorRow][col] = attr;
                cursorCol++;
            }
        }
    }

    /**
     * Write bytes to the screen at the current cursor position.
     */
    public void writeBytes(byte[] bytes, TN5250FieldAttr attr) {
        for (byte b : bytes) {
            if (cursorRow <= rows) {
                int col = cursorCol;
                if (col > cols) {
                    cursorRow++;
                    cursorCol = 1;
                    col = 1;
                    if (cursorRow > rows) { cursorRow = rows; break; }
                }
                int val = b & 0xFF;
                screen[cursorRow][col] = (val >= 0x20 && val <= 0x7E) ? (char) val : ' ';
                attrs[cursorRow][col] = attr;
                cursorCol++;
            }
        }
    }

    /**
     * Move cursor to a specific position (1-based).
     */
    public void cursorPosition(int row, int col) {
        cursorRow = Math.max(1, Math.min(row, rows));
        cursorCol = Math.max(1, Math.min(col, cols));
    }

    /**
     * Set the field attribute for a rectangular area.
     */
    public void setFieldAttrs(int startRow, int startCol, int endRow, int endCol, TN5250FieldAttr attr) {
        for (int r = Math.max(1, startRow); r <= Math.min(rows, endRow); r++) {
            for (int c = Math.max(1, startCol); c <= Math.min(cols, endCol); c++) {
                attrs[r][c] = attr;
            }
        }
    }

    /**
     * Clear a rectangular area (set to spaces with NORMAL attrs).
     */
    public void clearArea(int startRow, int startCol, int endRow, int endCol) {
        for (int r = Math.max(1, startRow); r <= Math.min(rows, endRow); r++) {
            for (int c = Math.max(1, startCol); c <= Math.min(cols, endCol); c++) {
                screen[r][c] = ' ';
                attrs[r][c] = TN5250FieldAttr.NORMAL;
            }
        }
    }

    // --- Getters ---

    /**
     * Get the character at a position.
     */
    public char charAt(int row, int col) {
        if (row >= 1 && row <= rows && col >= 1 && col <= cols) {
            return screen[row][col];
        }
        return ' ';
    }

    /**
     * Get the field attribute at a position.
     */
    public TN5250FieldAttr attrAt(int row, int col) {
        if (row >= 1 && row <= rows && col >= 1 && col <= cols) {
            return attrs[row][col];
        }
        return TN5250FieldAttr.NORMAL;
    }

    /**
     * Get the screen grid.
     */
    public char[][] getGrid() {
        char[][] grid = new char[rows + 1][];
        for (int r = 1; r <= rows; r++) {
            grid[r] = new char[cols + 1];
            System.arraycopy(screen[r], 0, grid[r], 0, cols + 1);
        }
        return grid;
    }

    /**
     * Get the attribute grid.
     */
    public TN5250FieldAttr[][] getAttrGrid() {
        TN5250FieldAttr[][] grid = new TN5250FieldAttr[rows + 1][];
        for (int r = 1; r <= rows; r++) {
            grid[r] = new TN5250FieldAttr[cols + 1];
            System.arraycopy(attrs[r], 0, grid[r], 0, cols + 1);
        }
        return grid;
    }

    /**
     * Get the current cursor position.
     */
    public int cursorRow() { return cursorRow; }
    public int cursorCol() { return cursorCol; }

    /**
     * Get the rows/cols.
     */
    public int rows() { return rows; }
    public int cols() { return cols; }

    /**
     * Check if the current cell is editable.
     */
    public boolean isEditable(int row, int col) {
        if (row >= 1 && row <= rows && col >= 1 && col <= cols) {
            return attrs[row][col].isEditable();
        }
        return false;
    }

    /**
     * Get the current field attribute at cursor.
     */
    public TN5250FieldAttr cursorAttr() {
        return attrAt(cursorRow, cursorCol);
    }

    /**
     * Render the screen as a list of lines.
     */
    public List<String> render() {
        List<String> lines = new ArrayList<>();
        for (int r = 1; r <= rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 1; c <= cols; c++) {
                sb.append(screen[r][c]);
            }
            lines.add(sb.toString());
        }
        return lines;
    }

    /**
     * Get the keyboard area.
     */
    public byte[] getKeyboardArea() {
        return keyboardArea.clone();
    }

    /**
     * Set the keyboard area.
     */
    public void setKeyboardArea(byte[] data) {
        System.arraycopy(data, 0, keyboardArea, 0, Math.min(data.length, 32));
    }

    /**
     * Apply a 5250 data stream record.
     *
     * <p>5250 data streams contain field-length encoded data with attribute bytes.
     *
     * @param data the 5250 data stream bytes
     * @param length the number of data bytes (after field length)
     * @param attr the field attribute for this data
     */
    public void applyFieldData(byte[] data, int length, TN5250FieldAttr attr) {
        byte[] fieldData = new byte[Math.min(length, data.length)];
        System.arraycopy(data, 0, fieldData, 0, fieldData.length);
        writeBytes(fieldData, attr);
    }

    /**
     * Get the rendered screen as a single string with line breaks.
     */
    public String renderToString() {
        return String.join("\n", render());
    }
}
