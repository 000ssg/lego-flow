package ssg.legoflow.network.terminals.base.display;

import java.util.Objects;

/**
 * Text attributes (SGR — Select Graphic Rendition).
 *
 * <p>Encodes all display properties for a single character cell:
 * foreground/background colors, intensity, style modifiers.
 *
 * <p>Color modes:
 * <ul>
 *   <li>Mode 0 (8-color): use {@link #foreground()} / {@link #background()} for values 0–7</li>
 *   <li>Mode 1 (256-color): use {@link #fgMode()} returns 1, {@link #fgColor()} for index 0–255</li>
 *   <li>Mode 2 (RGB): use {@link #fgMode()} returns 2, {@link #fgColor()} for 0xRRGGBB</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TermAttr {

    public static final TermAttr DEFAULT = new TermAttr(7, 0, 0, 0, false, false, false,
            TermAttr.UNDERLINE_NONE, false, false, false, false, 0, 0);

    public static final int BLACK = 0;
    public static final int RED = 1;
    public static final int GREEN = 2;
    public static final int YELLOW = 3;
    public static final int BLUE = 4;
    public static final int MAGENTA = 5;
    public static final int CYAN = 6;
    public static final int WHITE = 7;

    public static final int UNDERLINE_NONE = 0;
    public static final int UNDERLINE_SINGLE = 1;
    public static final int UNDERLINE_DOUBLE = 2;
    public static final int UNDERLINE_CURLY = 3;
    public static final int UNDERLINE_DOTTED = 4;
    public static final int UNDERLINE_DASHED = 5;

    private final int foreground;
    private final int background;
    private final int fgColor;
    private final int bgColor;
    private final boolean bold;
    private final boolean dim;
    private final boolean italic;
    private final int underline;
    private final boolean blink;
    private final boolean reverse;
    private final boolean hidden;
    private final boolean strikethrough;
    private final int fgMode;
    private final int bgMode;

    private TermAttr(int foreground, int background, int fgColor, int bgColor,
                     boolean bold, boolean dim, boolean italic, int underline,
                     boolean blink, boolean reverse, boolean hidden, boolean strikethrough,
                     int fgMode, int bgMode) {
        this.foreground = foreground;
        this.background = background;
        this.fgColor = fgColor;
        this.bgColor = bgColor;
        this.bold = bold;
        this.dim = dim;
        this.italic = italic;
        this.underline = underline;
        this.blink = blink;
        this.reverse = reverse;
        this.hidden = hidden;
        this.strikethrough = strikethrough;
        this.fgMode = fgMode;
        this.bgMode = bgMode;
    }

    /** 8-color foreground (0–7), used when {@code fgMode == 0}. */
    public int foreground() { return foreground; }

    /** 8-color background (0–7), used when {@code bgMode == 0}. */
    public int background() { return background; }

    /** Extended foreground color value (256-color index or RGB). */
    public int fgColor() { return fgColor; }

    /** Extended background color value (256-color index or RGB). */
    public int bgColor() { return bgColor; }

    /** Foreground color mode: 0=8-color, 1=256-color, 2=RGB. */
    public int fgMode() { return fgMode; }

    /** Background color mode: 0=8-color, 1=256-color, 2=RGB. */
    public int bgMode() { return bgMode; }

    public boolean bold() { return bold; }
    public boolean dim() { return dim; }
    public boolean italic() { return italic; }
    public int underline() { return underline; }
    public boolean blink() { return blink; }
    public boolean reverse() { return reverse; }
    public boolean hidden() { return hidden; }
    public boolean strikethrough() { return strikethrough; }

    public boolean isDefault() { return this == DEFAULT; }

    public Builder toBuilder() { return new Builder(this); }
    public static Builder builder() { return new Builder(null); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TermAttr that = (TermAttr) o;
        return foreground == that.foreground && background == that.background &&
                fgColor == that.fgColor && bgColor == that.bgColor &&
                fgMode == that.fgMode && bgMode == that.bgMode &&
                bold == that.bold && dim == that.dim && italic == that.italic &&
                underline == that.underline && blink == that.blink &&
                reverse == that.reverse && hidden == that.hidden &&
                strikethrough == that.strikethrough;
    }

    @Override
    public int hashCode() {
        return Objects.hash(foreground, background, fgColor, bgColor,
                fgMode, bgMode, bold, dim, italic, underline, blink, reverse, hidden, strikethrough);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TermAttr[");
        boolean first = true;
        if (bold) { sb.append("bold"); first = false; }
        if (dim) { append(sb, first, "dim"); first = false; }
        if (italic) { append(sb, first, "italic"); first = false; }
        if (underline != UNDERLINE_NONE) { append(sb, first, "ul=" + underline); first = false; }
        if (blink) { append(sb, first, "blink"); first = false; }
        if (reverse) { append(sb, first, "reverse"); first = false; }
        if (hidden) { append(sb, first, "hidden"); first = false; }
        if (strikethrough) { append(sb, first, "strike"); first = false; }
        if (fgMode > 0) { append(sb, first, "fg=" + fgMode + ":" + fgColor); first = false; }
        if (bgMode > 0) { append(sb, first, "bg=" + bgMode + ":" + bgColor); first = false; }
        if (fgMode == 0 && foreground != WHITE) { append(sb, first, "fg=" + foreground); first = false; }
        if (bgMode == 0 && background != BLACK) { append(sb, first, "bg=" + background); first = false; }
        if (first) sb.append("default");
        sb.append(']');
        return sb.toString();
    }

    private static void append(StringBuilder sb, boolean first, String text) {
        if (!first) sb.append(',');
        sb.append(text);
    }

    public static class Builder {
        private int foreground = WHITE;
        private int background = BLACK;
        private int fgColor = 0;
        private int bgColor = 0;
        private int fgMode = 0;
        private int bgMode = 0;
        private boolean bold;
        private boolean dim;
        private boolean italic;
        private int underline = UNDERLINE_NONE;
        private boolean blink;
        private boolean reverse;
        private boolean hidden;
        private boolean strikethrough;

        Builder(TermAttr base) {
            if (base != null) {
                this.foreground = base.foreground;
                this.background = base.background;
                this.fgColor = base.fgColor;
                this.bgColor = base.bgColor;
                this.fgMode = base.fgMode;
                this.bgMode = base.bgMode;
                this.bold = base.bold;
                this.dim = base.dim;
                this.italic = base.italic;
                this.underline = base.underline;
                this.blink = base.blink;
                this.reverse = base.reverse;
                this.hidden = base.hidden;
                this.strikethrough = base.strikethrough;
            }
        }

        public Builder reset() {
            foreground = WHITE; background = BLACK;
            fgColor = 0; bgColor = 0;
            fgMode = 0; bgMode = 0;
            bold = false; dim = false; italic = false;
            underline = UNDERLINE_NONE;
            blink = false; reverse = false; hidden = false; strikethrough = false;
            return this;
        }

        public Builder bold(boolean bold) { this.bold = bold; return this; }
        public Builder dim(boolean dim) { this.dim = dim; return this; }
        public Builder italic(boolean italic) { this.italic = italic; return this; }
        public Builder underline(int style) { this.underline = style; return this; }
        public Builder blink(boolean blink) { this.blink = blink; return this; }
        public Builder reverse(boolean reverse) { this.reverse = reverse; return this; }
        public Builder hidden(boolean hidden) { this.hidden = hidden; return this; }
        public Builder strikethrough(boolean strikethrough) { this.strikethrough = strikethrough; return this; }

        /** Set 8-color foreground (0–7). */
        public Builder foreground(int color) { this.fgMode = 0; this.foreground = Math.max(0, Math.min(7, color)); return this; }

        /** Set 8-color background (0–7). */
        public Builder background(int color) { this.bgMode = 0; this.background = Math.max(0, Math.min(7, color)); return this; }

        /** Set 256-color foreground (index 0–255). */
        public Builder foreground256(int index) { this.fgMode = 1; this.fgColor = Math.max(0, Math.min(255, index)); return this; }

        /** Set 256-color background (index 0–255). */
        public Builder background256(int index) { this.bgMode = 1; this.bgColor = Math.max(0, Math.min(255, index)); return this; }

        /** Set RGB foreground (0xRRGGBB). */
        public Builder foregroundRgb(int rgb) { this.fgMode = 2; this.fgColor = rgb & 0xFFFFFF; return this; }

        /** Set RGB background (0xRRGGBB). */
        public Builder backgroundRgb(int rgb) { this.bgMode = 2; this.bgColor = rgb & 0xFFFFFF; return this; }

        public TermAttr build() {
            return new TermAttr(foreground, background, fgColor, bgColor,
                    bold, dim, italic, underline, blink, reverse, hidden, strikethrough,
                    fgMode, bgMode);
        }
    }
}
