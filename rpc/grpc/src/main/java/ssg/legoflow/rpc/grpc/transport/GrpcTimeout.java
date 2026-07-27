package ssg.legoflow.rpc.grpc.transport;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Represents a gRPC timeout as specified in the grpc-timeout header.
 * Format: Timeout = TimeoutValue TimeoutUnit
 * TimeoutValue = positive integer
 * TimeoutUnit = "n" (nanoseconds) | "u" (microseconds) | "m" (milliseconds)
 *             | "S" (seconds) | "M" (minutes) | "H" (hours)
 */
public record GrpcTimeout(long value, TimeoutUnit unit) {

    private static final Pattern TIMEOUT_PATTERN = Pattern.compile("(\\d+)([nuSmMH])");

    public enum TimeoutUnit {
        NANOSECONDS("n", 1L),
        MICROSECONDS("u", 1_000L),
        MILLISECONDS("m", 1_000_000L),
        SECONDS("S", 1_000_000_000L),
        MINUTES("M", 60_000_000_000L),
        HOURS("H", 3_600_000_000_000L);

        private final String symbol;
        private final long nanosMultiplier;

        TimeoutUnit(String symbol, long nanosMultiplier) {
            this.symbol = symbol;
            this.nanosMultiplier = nanosMultiplier;
        }

        public String symbol() {
            return symbol;
        }

        public long nanosMultiplier() {
            return nanosMultiplier;
        }

        public static TimeoutUnit fromSymbol(String symbol) {
            return switch (symbol) {
                case "n" -> NANOSECONDS;
                case "u" -> MICROSECONDS;
                case "m" -> MILLISECONDS;
                case "S" -> SECONDS;
                case "M" -> MINUTES;
                case "H" -> HOURS;
                default -> throw new IllegalArgumentException("Unknown timeout unit: " + symbol);
            };
        }
    }

    /**
     * Converts this timeout to a Duration.
     */
    public Duration toDuration() {
        long nanos = value * unit.nanosMultiplier();
        return Duration.ofNanos(nanos);
    }

    /**
     * Converts this timeout to nanoseconds.
     */
    public long toNanos() {
        return value * unit.nanosMultiplier();
    }

    /**
     * Converts this timeout to milliseconds.
     */
    public long toMillis() {
        return toDuration().toMillis();
    }

    /**
     * Encodes as a grpc-timeout header value.
     */
    public String encode() {
        return value + unit.symbol();
    }

    /**
     * Parses a grpc-timeout header value.
     */
    public static GrpcTimeout parse(String headerValue) {
        if (headerValue == null || headerValue.isEmpty()) {
            return null;
        }
        var matcher = TIMEOUT_PATTERN.matcher(headerValue);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid grpc-timeout format: " + headerValue);
        }
        long val = Long.parseLong(matcher.group(1));
        var unit = TimeoutUnit.fromSymbol(matcher.group(2));
        return new GrpcTimeout(val, unit);
    }

    /**
     * Creates a timeout from a Duration.
     */
    public static GrpcTimeout fromDuration(Duration duration) {
        long nanos = duration.toNanos();
        if (nanos % 3_600_000_000_000L == 0 && nanos / 3_600_000_000_000L > 0) {
            return new GrpcTimeout(nanos / 3_600_000_000_000L, TimeoutUnit.HOURS);
        }
        if (nanos % 60_000_000_000L == 0 && nanos / 60_000_000_000L > 0) {
            return new GrpcTimeout(nanos / 60_000_000_000L, TimeoutUnit.MINUTES);
        }
        if (nanos % 1_000_000_000L == 0) {
            return new GrpcTimeout(nanos / 1_000_000_000L, TimeoutUnit.SECONDS);
        }
        if (nanos % 1_000_000L == 0) {
            return new GrpcTimeout(nanos / 1_000_000L, TimeoutUnit.MILLISECONDS);
        }
        if (nanos % 1_000L == 0) {
            return new GrpcTimeout(nanos / 1_000L, TimeoutUnit.MICROSECONDS);
        }
        return new GrpcTimeout(nanos, TimeoutUnit.NANOSECONDS);
    }

    /**
     * Creates a timeout in seconds.
     */
    public static GrpcTimeout ofSeconds(long seconds) {
        return new GrpcTimeout(seconds, TimeoutUnit.SECONDS);
    }

    /**
     * Creates a timeout in milliseconds.
     */
    public static GrpcTimeout ofMillis(long millis) {
        return new GrpcTimeout(millis, TimeoutUnit.MILLISECONDS);
    }

    @Override
    public String toString() {
        return encode();
    }
}
