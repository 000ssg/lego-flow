package ssg.legoflow.media.common.sdp;

import java.util.Objects;

/**
 * SDP bandwidth ({@code b=}) field as defined in RFC 4566 section 5.8.
 *
 * <p>Format: {@code b=<bwtype>:<bandwidth>}
 * Common types: CT (Conference Total), AS (Application-Specific).
 *
 * @param modifier the bandwidth modifier (e.g., "CT", "AS", or extension "X-" prefix)
 * @param value    the bandwidth value in kilobits per second
 * @since 1.0.0
 */
public record Bandwidth(String modifier, int value) {

    /**
     * Creates bandwidth with validation.
     */
    public Bandwidth {
        Objects.requireNonNull(modifier, "modifier");
        if (value < 0) {
            throw new IllegalArgumentException("Bandwidth value must be non-negative: " + value);
        }
    }

    /**
     * Parses bandwidth from a {@code b=} line value.
     *
     * @param line the bandwidth text (after {@code b=})
     * @return the parsed bandwidth
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Bandwidth parse(String line) {
        int colon = line.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Invalid b= line, expected ':' separator: " + line);
        }
        return new Bandwidth(line.substring(0, colon), Integer.parseInt(line.substring(colon + 1).trim()));
    }

    /**
     * Formats this bandwidth for SDP output (without the {@code b=} prefix).
     *
     * @return the formatted bandwidth string
     */
    public String format() {
        return modifier + ":" + value;
    }

    @Override
    public String toString() {
        return "b=" + format();
    }
}
