package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents an HTTP entity tag (ETag) such as {@code W/"xyz"} (weak) or {@code "xyz"} (strong).
 */
public record EntityTag(String value, boolean weak) {

    public EntityTag {
        Objects.requireNonNull(value, "value must not be null");
    }

    /**
     * Parses an entity tag string such as {@code W/"xyz"} or {@code "xyz"}.
     */
    public static EntityTag parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip();

        boolean weak = false;
        if (trimmed.startsWith("W/") || trimmed.startsWith("w/")) {
            weak = true;
            trimmed = trimmed.substring(2);
        }

        // Remove surrounding quotes
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        return new EntityTag(trimmed, weak);
    }

    /**
     * Checks whether this entity tag matches the given other tag.
     *
     * @param other            the other entity tag to compare with
     * @param strongComparison if true, both tags must be strong (not weak) to match;
     *                         if false, only the values are compared
     */
    public boolean matches(EntityTag other, boolean strongComparison) {
        Objects.requireNonNull(other, "other must not be null");
        if (strongComparison && (this.weak || other.weak)) {
            return false;
        }
        return this.value.equals(other.value);
    }

    @Override
    public String toString() {
        if (weak) {
            return "W/\"" + value + "\"";
        }
        return "\"" + value + "\"";
    }
}
