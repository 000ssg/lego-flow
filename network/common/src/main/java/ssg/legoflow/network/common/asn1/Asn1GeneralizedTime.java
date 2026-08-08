package ssg.legoflow.network.common.asn1;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * ASN.1 GeneralizedTime type (universal tag 0x18).
 *
 * <p>Represents a date and time as a string in the format {@code YYYYMMDDHHmmSS[.fff]Z}.
 * DER requires UTC (trailing Z) and no trailing fractional zeros.
 *
 * @param value the string representation of the time
 * @since 0.1.0
 */
public record Asn1GeneralizedTime(String value) implements Asn1Type {

    private static final DateTimeFormatter GENERALIZED_TIME_PARSER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyyMMddHHmmss")
                    .optionalStart()
                    .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
                    .optionalEnd()
                    .appendPattern("X")
                    .toFormatter();

    private static final DateTimeFormatter GENERALIZED_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss'Z'")
                    .withZone(ZoneOffset.UTC);

    /**
     * Creates a GeneralizedTime with validation.
     *
     * @param value the string value (must not be null)
     */
    public Asn1GeneralizedTime {
        if (value == null) {
            throw new IllegalArgumentException("Value must not be null");
        }
    }

    @Override
    public Asn1Tag tag() {
        return Asn1Tag.GENERALIZED_TIME;
    }

    /**
     * Creates a GeneralizedTime from an {@link Instant}.
     *
     * @param instant the instant
     * @return the GeneralizedTime
     */
    public static Asn1GeneralizedTime of(Instant instant) {
        return new Asn1GeneralizedTime(GENERALIZED_TIME_FORMATTER.format(instant));
    }

    /**
     * Creates a GeneralizedTime from a string value.
     *
     * @param value the string representation
     * @return the GeneralizedTime
     */
    public static Asn1GeneralizedTime of(String value) {
        return new Asn1GeneralizedTime(value);
    }

    /**
     * Parses this GeneralizedTime to an {@link Instant}.
     *
     * @return the parsed instant
     */
    public Instant toInstant() {
        return Instant.from(GENERALIZED_TIME_PARSER.parse(value));
    }
}
