package ssg.legoflow.network.terminals.base.display;

import java.util.Objects;

/**
 * A single character cell in the terminal screen buffer.
 *
 * <p>Each cell holds a Unicode code point (the displayed character)
 * and a {@link TermAttr} instance defining its visual properties.
 *
 * @since 0.2.0
 */
public final class Character {

    public static final Character EMPTY = new Character(' ', TermAttr.DEFAULT);

    private final int codepoint;
    private final TermAttr attr;

    /**
     * Create a character cell.
     *
     * @param codepoint Unicode code point
     * @param attr      text attributes
     */
    public Character(int codepoint, TermAttr attr) {
        this.codepoint = codepoint;
        this.attr = Objects.requireNonNull(attr, "attr must not be null");
    }

    /** Unicode code point of this cell. */
    public int codepoint() { return codepoint; }

    /** Text attributes of this cell. */
    public TermAttr attr() { return attr; }

    /** Convenience: return the character as a char (low 16 bits). */
    public char ch() { return (char) codepoint; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Character that = (Character) o;
        return codepoint == that.codepoint && Objects.equals(attr, that.attr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(codepoint, attr);
    }

    @Override
    public String toString() {
        return String.format("Character[%c/%04X] %s",
                codepoint < 32 ? '?' : (char) codepoint, codepoint, attr);
    }
}
