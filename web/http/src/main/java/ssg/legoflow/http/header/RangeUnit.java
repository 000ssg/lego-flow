package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents an HTTP range unit such as "bytes".
 */
public record RangeUnit(String unit) {

    public static final RangeUnit BYTES = new RangeUnit("bytes");

    public RangeUnit {
        Objects.requireNonNull(unit, "unit must not be null");
    }

    /**
     * Parses a range unit string.
     */
    public static RangeUnit parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip().toLowerCase();
        if ("bytes".equals(trimmed)) {
            return BYTES;
        }
        return new RangeUnit(trimmed);
    }

    @Override
    public String toString() {
        return unit;
    }
}
