package ssg.legoflow.network.terminals.base.config;

import java.util.Objects;

/**
 * Immutable configuration for a terminal emulator instance.
 *
 * <p>Defines display dimensions, color support, title, and behavioral flags.
 * Used as the single source of truth for terminal capabilities at creation time.
 *
 * @since 0.2.0
 */
public final class TerminalConfig {

    public static final int DEFAULT_ROWS = 24;
    public static final int DEFAULT_COLS = 80;
    public static final int DEFAULT_SCROLL_HISTORY = 512;

    private final int rows;
    private final int cols;
    private final int scrollHistory;
    private final int colorDepth;
    private final String title;
    private final String iconTitle;
    private final boolean autoWrap;
    private final boolean originMode;

    private TerminalConfig(Builder builder) {
        this.rows = builder.rows;
        this.cols = builder.cols;
        this.scrollHistory = builder.scrollHistory;
        this.colorDepth = builder.colorDepth;
        this.title = builder.title;
        this.iconTitle = builder.iconTitle;
        this.autoWrap = builder.autoWrap;
        this.originMode = builder.originMode;
    }

    /** Number of visible rows. */
    public int rows() { return rows; }

    /** Number of visible columns. */
    public int cols() { return cols; }

    /** Lines of scrollback history. */
    public int scrollHistory() { return scrollHistory; }

    /** Color depth: 0 = monochrome, 8 = palette(8), 16 = palette(16), 256 = 256-color, 24 = true color. */
    public int colorDepth() { return colorDepth; }

    /** Window title. */
    public String title() { return title; }

    /** Icon title (taskbar/dock label). */
    public String iconTitle() { return iconTitle; }

    /** Auto-wrap on space-at-right-edge. */
    public boolean autoWrap() { return autoWrap; }

    /** DEC origin mode (cursor addressing relative to scroll region top-left). */
    public boolean originMode() { return originMode; }

    /** Start building a new configuration with defaults. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "TerminalConfig{rows=" + rows + ", cols=" + cols + ", colorDepth=" + colorDepth +
                ", title=" + title + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TerminalConfig that = (TerminalConfig) o;
        return rows == that.rows && cols == that.cols &&
                scrollHistory == that.scrollHistory && colorDepth == that.colorDepth &&
                autoWrap == that.autoWrap && originMode == that.originMode &&
                Objects.equals(title, that.title) &&
                Objects.equals(iconTitle, that.iconTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rows, cols, scrollHistory, colorDepth, title, iconTitle, autoWrap, originMode);
    }

    // Builder
    public static class Builder {
        private int rows = DEFAULT_ROWS;
        private int cols = DEFAULT_COLS;
        private int scrollHistory = DEFAULT_SCROLL_HISTORY;
        private int colorDepth = 0;
        private String title = "";
        private String iconTitle = "";
        private boolean autoWrap = true;
        private boolean originMode = false;

        Builder() {}

        public Builder rows(int rows) {
            if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
            this.rows = rows;
            return this;
        }

        public Builder cols(int cols) {
            if (cols < 1) throw new IllegalArgumentException("cols must be >= 1");
            this.cols = cols;
            return this;
        }

        public Builder scrollHistory(int scrollHistory) {
            if (scrollHistory < 0) throw new IllegalArgumentException("scrollHistory must be >= 0");
            this.scrollHistory = scrollHistory;
            return this;
        }

        public Builder colorDepth(int colorDepth) {
            this.colorDepth = colorDepth;
            return this;
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNullElse(title, "");
            return this;
        }

        public Builder iconTitle(String iconTitle) {
            this.iconTitle = Objects.requireNonNullElse(iconTitle, "");
            return this;
        }

        public Builder autoWrap(boolean autoWrap) {
            this.autoWrap = autoWrap;
            return this;
        }

        public Builder originMode(boolean originMode) {
            this.originMode = originMode;
            return this;
        }

        public TerminalConfig build() {
            return new TerminalConfig(this);
        }
    }
}
