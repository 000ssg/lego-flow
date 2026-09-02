package ssg.legoflow.network.terminals.base.display;

import ssg.legoflow.network.terminals.base.config.TerminalConfig;
import java.util.List;
import java.util.Objects;
/**
 * High-level display model for terminal emulators.
 *
 * <p>Manages a {@link Screen} and applies common display operations:
 * character output, cursor motion, scrolling, and attribute changes.
 * This class is shared across all terminal type implementations.
 *
 * <p>Not thread-safe — all operations must be called from the
 * terminal's processing thread.
 *
 * @since 0.2.0
 */
public final class DisplayModel {

    private final TerminalConfig config;
    private final Screen screen;
    private TermAttr currentAttr = TermAttr.DEFAULT;
    private String title;
    private String iconTitle;
    private boolean originMode;

    public DisplayModel(TerminalConfig config) {
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.screen = new Screen(config.rows(), config.cols(), config.scrollHistory());
        this.title = config.title();
        this.iconTitle = config.iconTitle();
        this.originMode = config.originMode();
    }

    /** Access the underlying screen grid. */
    public Screen screen() { return screen; }

    /** Current text attributes. */
    public TermAttr currentAttr() { return currentAttr; }

    /** Set current text attributes. */
    public void setCurrentAttr(TermAttr attr) {
        this.currentAttr = Objects.requireNonNull(attr, "attr must not be null");
    }

    /** Window title. */
    public String title() { return title; }

    /** Set window title. */
    public void setTitle(String title) {
        this.title = Objects.requireNonNullElse(title, "");
    }

    /** Icon title. */
    public String iconTitle() { return iconTitle; }

    /** Set icon title. */
    public void setIconTitle(String iconTitle) {
        this.iconTitle = Objects.requireNonNullElse(iconTitle, "");
    }

    /** Cursor. */
    public Cursor cursor() { return screen.cursor(); }

    /**
     * Whether origin mode is active (cursor addressing relative to scroll region).
     */
    public boolean originMode() { return originMode; }

    /**
     * Set origin mode state (runtime override of config).
     */
    public void setOriginMode(boolean enabled) {
        this.originMode = enabled;
    }

    /**
     * Output a character at the current cursor position.
     *
     * @param ch the character code point
     */
    public void putChar(int ch) {
        screen.put(new Character(ch, currentAttr));
    }

    /**
     * Move cursor to absolute position (1-based).
     * Respects origin mode: if active, position is relative to scroll region top.
     */
    public void cursorPosition(int row, int col) {
        int r = originMode ? row + screen.scrollTop() - 1 : row;
        r = Math.max(1, Math.min(r, config.rows()));
        int c = Math.max(1, Math.min(col, config.cols()));
        screen.cursor().setPos(r, c);
        screen.setWrapPending(false);
    }

    /** Move cursor up. */
    public void cursorUp(int count) {
        int newRow = Math.max(screen.scrollTop(), screen.cursor().row() - count);
        screen.cursor().setPos(newRow, screen.cursor().col());
    }

    /** Move cursor down. */
    public void cursorDown(int count) {
        int newRow = Math.min(screen.scrollBottom(), screen.cursor().row() + count);
        screen.cursor().setPos(newRow, screen.cursor().col());
    }

    /** Move cursor forward. */
    public void cursorForward(int count) {
        int newCol = Math.min(config.cols(), screen.cursor().col() + count);
        screen.cursor().setPos(screen.cursor().row(), newCol);
    }

    /** Move cursor backward. */
    public void cursorBack(int count) {
        int newCol = Math.max(1, screen.cursor().col() - count);
        screen.cursor().setPos(screen.cursor().row(), newCol);
    }

    /** Home cursor (row 1, col 1 or scroll region top). */
    public void cursorHome() {
        int row = originMode ? screen.scrollTop() : 1;
        screen.cursor().setPos(row, 1);
    }

    /**
     * Erase in display.
     *
     * @param mode 0 = cursor to end, 1 = beginning to cursor, 2 = entire display
     */
    public void eraseDisplay(int mode) {
        TermAttr attr = currentAttr;
        Character space = new Character(' ', attr);
        Cursor cur = screen.cursor();

        if (mode == 0) {
            // Cursor to end
            eraseFrom(cur.row(), cur.col(), space);
        } else if (mode == 1) {
            // Beginning to cursor
            eraseTo(cur.row(), cur.col(), space);
        } else if (mode == 2) {
            // Entire display
            for (int r = 0; r < config.rows(); r++) {
                Character[] row = screen.getGrid()[r];
                for (int c = 0; c < config.cols(); c++) {
                    row[c] = space;
                }
            }
        }
    }

    /**
     * Erase in line.
     *
     * @param mode 0 = cursor to end of line, 1 = beginning to cursor, 2 = entire line
     */
    public void eraseLine(int mode) {
        TermAttr attr = currentAttr;
        Character space = new Character(' ', attr);
        Cursor cur = screen.cursor();
        int r = cur.row();

        if (mode == 0) {
            for (int c = cur.col(); c <= config.cols(); c++) {
                screen.getGrid()[r - 1][c - 1] = space;
            }
        } else if (mode == 1) {
            for (int c = 1; c <= cur.col(); c++) {
                screen.getGrid()[r - 1][c - 1] = space;
            }
        } else if (mode == 2) {
            for (int c = 1; c <= config.cols(); c++) {
                screen.getGrid()[r - 1][c - 1] = space;
            }
        }
    }

    private void eraseFrom(int startRow, int startCol, Character space) {
        for (int r = startRow; r <= config.rows(); r++) {
            int fromCol = (r == startRow) ? startCol : 1;
            for (int c = fromCol; c <= config.cols(); c++) {
                screen.getGrid()[r - 1][c - 1] = space;
            }
        }
    }

    private void eraseTo(int endRow, int endCol, Character space) {
        for (int r = 1; r <= endRow; r++) {
            int toCol = (r == endRow) ? endCol : config.cols();
            for (int c = 1; c <= toCol; c++) {
                screen.getGrid()[r - 1][c - 1] = space;
            }
        }
    }

    /** Scroll up (shift lines up, new blank line at bottom). */
    public void scrollUp() { screen.scrollUp(); }

    /** Scroll down (shift lines down, new blank line at top). */
    public void scrollDown() { screen.scrollDown(); }

    /** Clear entire display and history. */
    public void clear() {
        screen.clear();
        screen.cursor().setPos(1, 1);
    }

    /** Render the visible portion as a list of strings. */
    public List<String> render() {
        List<String> lines = screen.renderAll();
        // Return only the visible portion (last `rows` lines)
        List<String> visible = new java.util.ArrayList<>();
        int start = Math.max(0, lines.size() - config.rows());
        for (int i = start; i < lines.size(); i++) {
            visible.add(lines.get(i));
        }
        while (visible.size() < config.rows()) {
            visible.add(0, "");
        }
        return visible;
    }

    /** Terminal configuration. */
    public TerminalConfig config() { return config; }
}
