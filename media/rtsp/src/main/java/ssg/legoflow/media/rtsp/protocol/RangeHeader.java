package ssg.legoflow.media.rtsp.protocol;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Parsed RTSP Range header as defined in RFC 7826 section 18.40.
 *
 * <p>Supports three range formats:
 * <ul>
 *   <li>{@code npt=<start>-<end>} -- Normal Play Time (seconds or hh:mm:ss.frac)</li>
 *   <li>{@code clock=<start>-<end>} -- Absolute (UTC) time</li>
 *   <li>{@code smpte=<start>-<end>} -- SMPTE timecodes</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class RangeHeader {

    /**
     * Range type.
     */
    public enum Type {
        /** Normal Play Time (seconds). */
        NPT,
        /** Absolute (UTC) time. */
        CLOCK,
        /** SMPTE timecodes. */
        SMPTE
    }

    private final Type type;
    private final String startTime;
    private final Optional<String> endTime;

    /**
     * Creates a range header.
     *
     * @param type      the range type
     * @param startTime the start time string
     * @param endTime   the optional end time string
     */
    public RangeHeader(Type type, String startTime, Optional<String> endTime) {
        this.type = Objects.requireNonNull(type, "type");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
    }

    /**
     * Creates an NPT range starting from a given time.
     *
     * @param startSeconds the start time in seconds
     * @return the range header
     */
    public static RangeHeader nptFrom(double startSeconds) {
        return new RangeHeader(Type.NPT, formatNpt(startSeconds), Optional.empty());
    }

    /**
     * Creates an NPT range from start to end.
     *
     * @param startSeconds the start time in seconds
     * @param endSeconds   the end time in seconds
     * @return the range header
     */
    public static RangeHeader nptRange(double startSeconds, double endSeconds) {
        return new RangeHeader(Type.NPT, formatNpt(startSeconds), Optional.of(formatNpt(endSeconds)));
    }

    /**
     * Creates an NPT range starting from the beginning.
     *
     * @return the range header for npt=0-
     */
    public static RangeHeader nptBeginning() {
        return new RangeHeader(Type.NPT, "0", Optional.empty());
    }

    /** Returns the range type. */
    public Type type() { return type; }

    /** Returns the start time string. */
    public String startTime() { return startTime; }

    /** Returns the optional end time string. */
    public Optional<String> endTime() { return endTime; }

    /**
     * Parses the start time as seconds (NPT format only).
     *
     * @return the start time in seconds, or empty if not NPT or not parseable
     */
    public OptionalDouble startAsSeconds() {
        if (type != Type.NPT) return OptionalDouble.empty();
        return parseNptSeconds(startTime);
    }

    /**
     * Parses a Range header value.
     *
     * @param value the Range header value (e.g., "npt=0-", "clock=...", "smpte=...")
     * @return the parsed range header
     * @throws IllegalArgumentException if the format is invalid
     */
    public static RangeHeader parse(String value) {
        int eq = value.indexOf('=');
        if (eq < 0) {
            throw new IllegalArgumentException("Invalid Range header, missing '=': " + value);
        }
        String typeStr = value.substring(0, eq).trim().toLowerCase();
        String range = value.substring(eq + 1).trim();

        Type type = switch (typeStr) {
            case "npt" -> Type.NPT;
            case "clock" -> Type.CLOCK;
            case "smpte" -> Type.SMPTE;
            default -> throw new IllegalArgumentException("Unknown range type: " + typeStr);
        };

        int dash = range.indexOf('-');
        if (dash < 0) {
            return new RangeHeader(type, range, Optional.empty());
        }

        String start = range.substring(0, dash).trim();
        String end = range.substring(dash + 1).trim();
        return new RangeHeader(type, start, end.isEmpty() ? Optional.empty() : Optional.of(end));
    }

    /**
     * Formats this range header as a string value.
     *
     * @return the formatted Range header value
     */
    public String format() {
        String prefix = switch (type) {
            case NPT -> "npt";
            case CLOCK -> "clock";
            case SMPTE -> "smpte";
        };
        return prefix + "=" + startTime + "-" + endTime.orElse("");
    }

    private static String formatNpt(double seconds) {
        if (seconds == (long) seconds) {
            return String.valueOf((long) seconds);
        }
        return String.valueOf(seconds);
    }

    private static OptionalDouble parseNptSeconds(String npt) {
        try {
            // Try simple numeric format first
            return OptionalDouble.of(Double.parseDouble(npt));
        } catch (NumberFormatException e) {
            // Try hh:mm:ss.frac format
            try {
                String[] parts = npt.split(":");
                if (parts.length == 3) {
                    double hours = Double.parseDouble(parts[0]);
                    double minutes = Double.parseDouble(parts[1]);
                    double seconds = Double.parseDouble(parts[2]);
                    return OptionalDouble.of(hours * 3600 + minutes * 60 + seconds);
                }
            } catch (NumberFormatException ignored) {
                // Fall through
            }
            return OptionalDouble.empty();
        }
    }

    @Override
    public String toString() {
        return "Range: " + format();
    }
}
