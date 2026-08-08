package ssg.legoflow.media.common.sdp;

import java.util.List;
import java.util.Objects;

/**
 * SDP repeat time ({@code r=}) field as defined in RFC 4566 section 5.10.
 *
 * <p>Format: {@code r=<repeat interval> <active duration> <offsets from start-time>}
 * All values are in seconds (or use compact notation: d=days, h=hours, m=minutes).
 *
 * @param repeatInterval the repeat interval in seconds
 * @param activeDuration the active duration in seconds
 * @param offsets        offsets from start-time in seconds
 * @since 0.1.0
 */
public record RepeatTime(String repeatInterval, String activeDuration, List<String> offsets) {

    /**
     * Creates a repeat time with validation.
     */
    public RepeatTime {
        Objects.requireNonNull(repeatInterval, "repeatInterval");
        Objects.requireNonNull(activeDuration, "activeDuration");
        Objects.requireNonNull(offsets, "offsets");
        offsets = List.copyOf(offsets);
    }

    /**
     * Parses repeat time from an {@code r=} line value.
     *
     * @param line the repeat time text (after {@code r=})
     * @return the parsed repeat time
     * @throws IllegalArgumentException if the format is invalid
     */
    public static RepeatTime parse(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid r= line, expected at least 3 fields: " + line);
        }
        return new RepeatTime(parts[0], parts[1], List.of(parts).subList(2, parts.length));
    }

    /**
     * Formats this repeat time for SDP output (without the {@code r=} prefix).
     *
     * @return the formatted repeat time string
     */
    public String format() {
        var sb = new StringBuilder();
        sb.append(repeatInterval).append(' ').append(activeDuration);
        for (String offset : offsets) {
            sb.append(' ').append(offset);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "r=" + format();
    }
}
