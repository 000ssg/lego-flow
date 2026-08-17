package ssg.legoflow.network.terminals.base.display;

import java.util.Objects;

/**
 * Cursor position and state within the terminal display.
 *
 * <p>Row and column are 1-based (terminal convention). The cursor
 * position is mutable — it tracks the current insertion point.
 *
 * @since 0.2.0
 */
public final class Cursor {

    private int row;
    private int col;
    private boolean visible = true;

    /**
     * Create a cursor at the given position (1-based).
     *
     * @param row row number (1-based)
     * @param col column number (1-based)
     */
    public Cursor(int row, int col) {
        this.row = row;
        this.col = col;
    }

    /** Current row (1-based). */
    public int row() { return row; }

    /** Current column (1-based). */
    public int col() { return col; }

    /** Whether the cursor is visible on screen. */
    public boolean visible() { return visible; }

    /** Set cursor position. */
    public void setPos(int row, int col) {
        this.row = row;
        this.col = col;
    }

    /** Show the cursor. */
    public void show() { this.visible = true; }

    /** Hide the cursor. */
    public void hide() { this.visible = false; }

    /** Toggle cursor visibility. */
    public void toggle() { this.visible = !visible; }

    /** Move cursor up by {@code rows}. */
    public void up(int rows) {
        this.row = Math.max(1, this.row - rows);
    }

    /** Move cursor down by {@code rows}. */
    public void down(int rows) {
        this.row += rows;
    }

    /** Move cursor forward by {@code cols}. */
    public void forward(int cols) {
        this.col += cols;
    }

    /** Move cursor backward by {@code cols}. */
    public void back(int cols) {
        this.col = Math.max(1, this.col - cols);
    }

    /** Clone this cursor state. */
    @Override
    public Cursor clone() {
        Cursor c = new Cursor(row, col);
        c.visible = visible;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cursor cursor = (Cursor) o;
        return row == cursor.row && col == cursor.col && visible == cursor.visible;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col, visible);
    }

    @Override
    public String toString() {
        return "Cursor{row=" + row + ", col=" + col + ", visible=" + visible + '}';
    }
}
