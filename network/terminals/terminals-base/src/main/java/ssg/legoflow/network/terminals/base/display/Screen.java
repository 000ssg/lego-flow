package ssg.legoflow.network.terminals.base.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
/**
 * 2D character grid with scroll history buffer.
 *
 * <p>The screen maintains:
 * <ul>
 *   <li>A fixed-size visible grid (rows × cols)</li>
 *   <li>A scrollback history buffer (deque of line rows)</li>
 *   <li>A configurable scroll region within the visible grid</li>
 *   <li>Cursor position tracking</li>
 *   <li>Wrap state (whether cursor is at right edge and ready to wrap)</li>
 * </ul>
 *
 * <p>All row/column indices are 1-based (terminal convention).
 *
 * @since 0.2.0
 */
public final class Screen {

    private final int rows;
    private final int cols;

    /** Visible grid — row 1 is at index 0. */
    private final Character[][] grid;

    /** Scrollback history (oldest first). */
    private final Deque<Character[]> history;
    private final int maxHistory;

    /** Scroll region bounds (1-based, inclusive). */
    private int scrollTop;
    private int scrollBottom;

    /** Cursor. */
    private final Cursor cursor;

    /** True if the current line is wrapped (next character wraps to next line). */
    private boolean wrapPending;

    /** Tracks which lines in history are wrapped. */
    private final Deque<Boolean> historyWrapFlags;

    public Screen(int rows, int cols, int maxHistory) {
        if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
        if (cols < 1) throw new IllegalArgumentException("cols must be >= 1");
        if (maxHistory < 0) throw new IllegalArgumentException("maxHistory must be >= 0");

        this.rows = rows;
        this.cols = cols;
        this.maxHistory = maxHistory;

        this.grid = new Character[rows][cols];
        fillDefault();

        this.history = new ArrayDeque<>(maxHistory);
        this.historyWrapFlags = new ArrayDeque<>(maxHistory);
        this.scrollTop = 1;
        this.scrollBottom = rows;
        this.cursor = new Cursor(1, 1);
        this.wrapPending = false;
    }

    /** Fill the entire visible grid with default empty cells. */
    private void fillDefault() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = Character.EMPTY;
            }
        }
    }

    public int rows() { return rows; }
    public int cols() { return cols; }
    public int historySize() { return history.size(); }
    public int scrollTop() { return scrollTop; }
    public int scrollBottom() { return scrollBottom; }
    public Cursor cursor() { return cursor; }
    public boolean wrapPending() { return wrapPending; }

    /** Set wrap-pending state. */
    public void setWrapPending(boolean wrap) { this.wrapPending = wrap; }

    /** Set scroll region (1-based, inclusive). */
    public void setScrollRegion(int top, int bottom) {
        int t = Math.max(1, Math.min(top, rows));
        int b = Math.max(t, Math.min(bottom, rows));
        scrollBottom = b;
        scrollTop = t;
        // Reset cursor to home if outside new scroll region
        if (cursor.row() < t || cursor.row() > b) {
            cursor.setPos(t, 1);
        }
    }

    /** Get character at position (1-based). */
    public Character at(int row, int col) {
        if (row < 1 || row > rows || col < 1 || col > cols) {
            return Character.EMPTY;
        }
        return grid[row - 1][col - 1];
    }

    /**
     * Put a character at the current cursor position.
     * Handles auto-wrap and scrolling.
     */
    public void put(Character ch) {
        // Handle wrap-pending: advance to next line BEFORE writing
        if (wrapPending) {
            wrapPending = false;
            cursor.setPos(cursor.row() + 1, 1);
        }

        // Write character at current cursor position
        int r = cursor.row() - 1;
        int c = cursor.col() - 1;
        if (r >= 0 && r < rows && c >= 0 && c < cols) {
            grid[r][c] = ch;
        }

        // Advance cursor
        if (cursor.col() >= cols) {
            wrapPending = true;
        } else {
            cursor.forward(1);
        }

        // Check scroll
        if (cursor.row() > scrollBottom) {
            scrollDown();
            cursor.setPos(scrollBottom, cursor.col());
        }
    }

    /** Scroll the visible region down by one line. */
    void scrollDown() {
        if (scrollTop > scrollBottom) return;

        // Save top line to history
        Character[] topLine = grid[scrollTop - 1];
        Character[] saved = new Character[cols];
        System.arraycopy(topLine, 0, saved, 0, cols);
        history.addFirst(saved);
        historyWrapFlags.addFirst(wrapPending);
        if (history.size() > maxHistory) {
            history.removeLast();
            historyWrapFlags.removeLast();
        }

        // Shift lines down
        for (int r = scrollBottom - 1; r > scrollTop - 1; r--) {
            System.arraycopy(grid[r - 1], 0, grid[r], 0, cols);
        }

        // Clear the top line of scroll region
        for (int c = 0; c < cols; c++) {
            grid[scrollTop - 1][c] = Character.EMPTY;
        }
    }

    /** Scroll the visible region up by one line. */
    void scrollUp() {
        if (scrollTop > scrollBottom) return;

        // Save bottom line to history
        Character[] bottomLine = grid[scrollBottom - 1];
        Character[] saved = new Character[cols];
        System.arraycopy(bottomLine, 0, saved, 0, cols);
        history.addFirst(saved);
        historyWrapFlags.addFirst(false);
        if (history.size() > maxHistory) {
            history.removeLast();
            historyWrapFlags.removeLast();
        }

        // Shift lines up
        for (int r = scrollTop - 1; r < scrollBottom - 1; r++) {
            System.arraycopy(grid[r + 1], 0, grid[r], 0, cols);
        }

        // Clear the bottom line of scroll region
        for (int c = 0; c < cols; c++) {
            grid[scrollBottom - 1][c] = Character.EMPTY;
        }
    }

    /** Insert blank lines at cursor row, scrolling region down. */
    public void insertLines(int count) {
        for (int i = 0; i < count; i++) {
            // Save bottom line
            Character[] bottomLine = grid[scrollBottom - 1];
            Character[] saved = new Character[cols];
            System.arraycopy(bottomLine, 0, saved, 0, cols);

            // Shift region down
            for (int r = scrollBottom - 1; r > cursor.row() - 1; r--) {
                System.arraycopy(grid[r - 1], 0, grid[r], 0, cols);
            }

            // Clear cursor line
            int cr = cursor.row() - 1;
            for (int c = 0; c < cols; c++) {
                grid[cr][c] = Character.EMPTY;
            }
        }
    }

    /** Delete lines at cursor row, scrolling region up. */
    public void deleteLines(int count) {
        for (int i = 0; i < count; i++) {
            // Shift region up
            for (int r = cursor.row() - 1; r < scrollBottom - 1; r++) {
                System.arraycopy(grid[r + 1], 0, grid[r], 0, cols);
            }

            // Clear bottom line
            for (int c = 0; c < cols; c++) {
                grid[scrollBottom - 1][c] = Character.EMPTY;
            }
        }
    }

    /** Insert blank characters at cursor position. */
    public void insertChars(int count) {
        int r = cursor.row() - 1;
        int c = cursor.col() - 1;
        for (int i = 0; i < count; i++) {
            // Shift right
            for (int col = cols - 1; col > c; col--) {
                grid[r][col] = grid[r][col - 1];
            }
            grid[r][c] = Character.EMPTY;
        }
    }

    /** Delete characters at cursor position, shifting left. */
    public void deleteChars(int count) {
        int r = cursor.row() - 1;
        int c = cursor.col() - 1;
        for (int i = 0; i < count; i++) {
            for (int col = c; col < cols - 1; col++) {
                grid[r][col] = grid[r][col + 1];
            }
            grid[r][cols - 1] = Character.EMPTY;
        }
    }

    /** Erase characters at cursor position (replace with space + current attr). */
    public void eraseChars(int count) {
        int r = cursor.row() - 1;
        int c = cursor.col() - 1;
        TermAttr attr = at(cursor.row(), cursor.col()).attr();
        for (int i = 0; i < count; i++) {
            int col = Math.min(c + i, cols - 1);
            grid[r][col] = new Character(' ', attr);
        }
    }

    /** Clear entire visible grid. */
    public void clear() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                grid[r][c] = Character.EMPTY;
            }
        }
        history.clear();
        historyWrapFlags.clear();
    }

    /** Get the visible grid row as a string (for debugging). */
    public String rowString(int row) {
        if (row < 1 || row > rows) return "";
        StringBuilder sb = new StringBuilder();
        for (int c = 0; c < cols; c++) {
            char ch = grid[row - 1][c].ch();
            sb.append(ch < 32 && ch > 0 ? '·' : ch);
        }
        return sb.toString();
    }

    /**
     * Render the full display (history + visible) as a list of lines.
     * Returns lines from oldest history entry to current visible grid.
     */
    public List<String> renderAll() {
        List<String> lines = new ArrayList<>();
        for (Character[] line : history) {
            lines.add(renderLine(line));
        }
        for (int r = 0; r < rows; r++) {
            lines.add(renderLine(grid[r]));
        }
        return lines;
    }

    private static String renderLine(Character[] row) {
        StringBuilder sb = new StringBuilder();
        for (Character ch : row) {
            if (ch == null) {
                sb.append(' ');
            } else {
                char c = ch.ch();
                sb.append(c < 32 && c > 0 ? '·' : c);
            }
        }
        // Trim trailing spaces for readability
        int len = sb.length();
        while (len > 0 && sb.charAt(len - 1) == ' ') len--;
        return sb.substring(0, len);
    }

    /** Get the visible grid content as a 2D array (for testing). */
    /** Get the visible grid content (shared reference). */
    public Character[][] getGrid() { return grid; }

    /** Get history as list of strings (for testing). */
    public List<String> getHistory() {
        List<String> result = new ArrayList<>(history.size());
        for (Character[] line : history) {
            result.add(renderLine(line));
        }
        // history deque is ordered oldest-first (addFirst), so reverse for display
        Collections.reverse(result);
        return result;
    }
}
