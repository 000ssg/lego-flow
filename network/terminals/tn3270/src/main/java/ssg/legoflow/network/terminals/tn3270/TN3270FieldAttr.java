package ssg.legoflow.network.terminals.tn3270;

/**
 * 3270 field attributes.
 *
 * <p>Each 3270 field has two attribute bytes:
 * <ul>
 *   <li>Primary attribute: field type (normal, read-only, bold, underscore, etc.)
 *   <li>Secondary attribute: color/intensity (emphasized, underline, reverse, etc.)
 * </ul>
 *
 * <p>Combined, these define the visual appearance and editability of each field.
 *
 * @since 0.2.0
 */
public final class TN3270FieldAttr {

    /** Normal (plain) field. */
    public static final TN3270FieldAttr NORMAL = new TN3270FieldAttr(0x00, 0x00, "normal");

    /** Read-only field (not editable). */
    public static final TN3270FieldAttr READ_ONLY = new TN3270FieldAttr(0x01, 0x00, "readOnly");

    /** Protected (unprotected) field — equivalent to read-only. */
    public static final TN3270FieldAttr PROTECTED = READ_ONLY;

    /** Bold attribute. */
    public static final TN3270FieldAttr BOLD = new TN3270FieldAttr(0x02, 0x00, "bold");

    /** Underline attribute. */
    public static final TN3270FieldAttr UNDERLINE = new TN3270FieldAttr(0x04, 0x00, "underline");

    /** Reverse video attribute. */
    public static final TN3270FieldAttr REVERSE = new TN3270FieldAttr(0x08, 0x00, "reverse");

    /** Dark/black attribute. */
    public static final TN3270FieldAttr DARK = new TN3270FieldAttr(0x10, 0x00, "dark");

    /** Flash/blink attribute. */
    public static final TN3270FieldAttr FLASH = new TN3270FieldAttr(0x20, 0x00, "flash");

    /** Hole-punch attribute (printer output marker). */
    public static final TN3270FieldAttr HOLE = new TN3270FieldAttr(0x40, 0x00, "hole");

    /** Secondary attribute: standard intensity. */
    public static final TN3270FieldAttr NORMAL_SECONDARY = new TN3270FieldAttr(0x00, 0x01, "normalSecondary");

    /** Secondary attribute: not emphasized. */
    public static final TN3270FieldAttr NOT_EMPHASIZED = new TN3270FieldAttr(0x00, 0x02, "notEmphasized");

    /** Secondary attribute: italic. */
    public static final TN3270FieldAttr ITALIC = new TN3270FieldAttr(0x00, 0x03, "italic");

    /** Secondary attribute: standard background. */
    public static final TN3270FieldAttr NORMAL_BG = new TN3270FieldAttr(0x00, 0x10, "normalBg");

    /** Secondary attribute: blue background. */
    public static final TN3270FieldAttr BLUE_BG = new TN3270FieldAttr(0x00, 0x11, "blueBg");

    /** Secondary attribute: purple background. */
    public static final TN3270FieldAttr PURPLE_BG = new TN3270FieldAttr(0x00, 0x12, "purpleBg");

    /** Secondary attribute: green background. */
    public static final TN3270FieldAttr GREEN_BG = new TN3270FieldAttr(0x00, 0x13, "greenBg");

    /** Secondary attribute: cyan background. */
    public static final TN3270FieldAttr CYAN_BG = new TN3270FieldAttr(0x00, 0x14, "cyanBg");

    /** Secondary attribute: red background. */
    public static final TN3270FieldAttr RED_BG = new TN3270FieldAttr(0x00, 0x15, "redBg");

    /** Secondary attribute: yellow background. */
    public static final TN3270FieldAttr YELLOW_BG = new TN3270FieldAttr(0x00, 0x16, "yellowBg");

    /** Secondary attribute: white background. */
    public static final TN3270FieldAttr WHITE_BG = new TN3270FieldAttr(0x00, 0x17, "whiteBg");

    private final int primaryAttr;
    private final int secondaryAttr;
    private final String label;

    TN3270FieldAttr(int primary, int secondary, String label) {
        this.primaryAttr = primary;
        this.secondaryAttr = secondary;
        this.label = label;
    }

    /**
     * Get the primary attribute byte.
     */
    public int primary() { return primaryAttr; }

    /**
     * Get the secondary attribute byte.
     */
    public int secondary() { return secondaryAttr; }

    /**
     * Get the human-readable label.
     */
    public String label() { return label; }

    /**
     * Check if the field is editable (read-write).
     * The 3270 protect bit (0x01) determines editability; display
     * attributes like bold/underline do not affect editability.
     */
    public boolean isEditable() { return (primaryAttr & 0x01) == 0; }

    /**
     * Check if the field has the bold bit set.
     */
    public boolean isBold() { return (primaryAttr & 0x02) != 0; }

    /**
     * Check if the field has the underline bit set.
     */
    public boolean isUnderlined() { return (primaryAttr & 0x04) != 0; }

    /**
     * Check if the field has the reverse bit set.
     */
    public boolean isReversed() { return (primaryAttr & 0x08) != 0; }

    /**
     * Check if the field has the flash/blink bit set.
     */
    public boolean isFlashing() { return (primaryAttr & 0x20) != 0; }

    /**
     * Check if the field is read-only/protected.
     * The protect bit (0x01) in the primary attribute determines this.
     */
    public boolean isProtected() { return (primaryAttr & 0x01) != 0; }

    /**
     * Combine primary and secondary attributes to create a new field attr.
     */
    public TN3270FieldAttr withSecondary(int secondary) {
        return new TN3270FieldAttr(this.primaryAttr, secondary, label + "+secondary" + secondary);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TN3270FieldAttr)) return false;
        TN3270FieldAttr that = (TN3270FieldAttr) o;
        return primaryAttr == that.primaryAttr && secondaryAttr == that.secondaryAttr;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(primaryAttr, secondaryAttr);
    }

    @Override
    public String toString() {
        return "FieldAttr(0x" + String.format("%02X", primaryAttr) +
               ", 0x" + String.format("%02X", secondaryAttr) + ") [" + label + "]";
    }
}
