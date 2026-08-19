package ssg.legoflow.network.terminals.tn5250;

/**
 * TN5250 (5250) field attributes.
 *
 * <p>5250 fields have three attribute bits:
 * <ul>
 *   <li>Emphasis — field is emphasized (bold/highlighted)</li>
 *   <li>Automatic Skip — cursor automatically skips to next field</li>
 *   <li>Blank — field is blank/protected</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class TN5250FieldAttr {

    /** Normal field — editable, not emphasized, no auto-skip. */
    public static final TN5250FieldAttr NORMAL = new TN5250FieldAttr(false, false, false);

    /** Emphasized field — visually highlighted. */
    public static final TN5250FieldAttr EMPHASIS = new TN5250FieldAttr(true, false, false);

    /** Automatic skip — cursor moves to next field on full. */
    public static final TN5250FieldAttr AUTO_SKIP = new TN5250FieldAttr(false, true, false);

    /** Emphasized + auto-skip. */
    public static final TN5250FieldAttr EMPHASIS_AUTO_SKIP = new TN5250FieldAttr(true, true, false);

    /** Blank/protected — not editable. */
    public static final TN5250FieldAttr BLANK = new TN5250FieldAttr(false, false, true);

    /** Emphasized + blank. */
    public static final TN5250FieldAttr EMPHASIS_BLANK = new TN5250FieldAttr(true, false, true);

    /** All attributes combined. */
    public static final TN5250FieldAttr FULL = new TN5250FieldAttr(true, true, true);

    private final boolean emphasis;
    private final boolean autoSkip;
    private final boolean blank;

    private TN5250FieldAttr(boolean emphasis, boolean autoSkip, boolean blank) {
        this.emphasis = emphasis;
        this.autoSkip = autoSkip;
        this.blank = blank;
    }

    public boolean isEmphasized() { return emphasis; }
    public boolean isAutoSkip() { return autoSkip; }
    public boolean isBlank() { return blank; }
    public boolean isEditable() { return !blank; }

    /**
     * Encode to the 5250 attribute byte.
     *
     * <p>5250 attribute byte format:
     * <pre>
     *   Bit 0: Emphasis
     *   Bit 1: Automatic Skip
     *   Bit 2: Blank (not Editable)
     *   Bits 3-7: Reserved (always 0)
     * </pre>
     */
    public int encode() {
        int val = 0;
        if (emphasis) val |= 0x01;
        if (autoSkip) val |= 0x02;
        if (blank) val |= 0x04;
        return val;
    }

    /**
     * Decode from a 5250 attribute byte.
     */
    public static TN5250FieldAttr decode(int attrByte) {
        boolean emphasis = (attrByte & 0x01) != 0;
        boolean autoSkip = (attrByte & 0x02) != 0;
        boolean blank = (attrByte & 0x04) != 0;
        return new TN5250FieldAttr(emphasis, autoSkip, blank);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TN5250FieldAttr)) return false;
        TN5250FieldAttr that = (TN5250FieldAttr) o;
        return emphasis == that.emphasis && autoSkip == that.autoSkip && blank == that.blank;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(emphasis, autoSkip, blank);
    }

    @Override
    public String toString() {
        return "FieldAttr(emphasis=" + emphasis + ", autoSkip=" + autoSkip + ", blank=" + blank + ")";
    }
}
