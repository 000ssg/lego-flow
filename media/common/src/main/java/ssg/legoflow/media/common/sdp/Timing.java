package ssg.legoflow.media.common.sdp;

/**
 * SDP timing ({@code t=}) field as defined in RFC 4566 section 5.9.
 *
 * <p>Format: {@code t=<start-time> <stop-time>}
 * Times are NTP timestamps (seconds since 1900-01-01). A value of 0 means
 * unbounded (permanent session when both are 0).
 *
 * @param startTime NTP start timestamp (0 for unbounded)
 * @param stopTime  NTP stop timestamp (0 for unbounded)
 * @since 0.1.0
 */
public record Timing(long startTime, long stopTime) {

    /** Permanent session timing (both start and stop are 0). */
    public static final Timing PERMANENT = new Timing(0, 0);

    /**
     * Parses timing from a {@code t=} line value.
     *
     * @param line the timing text (after {@code t=})
     * @return the parsed timing
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Timing parse(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid t= line, expected 2 fields: " + line);
        }
        return new Timing(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }

    /**
     * Formats this timing for SDP output (without the {@code t=} prefix).
     *
     * @return the formatted timing string
     */
    public String format() {
        return startTime + " " + stopTime;
    }

    @Override
    public String toString() {
        return "t=" + format();
    }
}
