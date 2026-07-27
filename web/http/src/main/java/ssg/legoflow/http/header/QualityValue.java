package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents a value with an associated quality factor, as used in HTTP content negotiation
 * (e.g., "text/html;q=0.9").
 */
public final class QualityValue implements Comparable<QualityValue> {

    private final String value;
    private final double quality;

    public QualityValue(String value, double quality) {
        Objects.requireNonNull(value, "value must not be null");
        if (quality < 0.0 || quality > 1.0) {
            throw new IllegalArgumentException("quality must be between 0.0 and 1.0, got: " + quality);
        }
        this.value = value;
        this.quality = quality;
    }

    public QualityValue(String value) {
        this(value, 1.0);
    }

    /**
     * Parses a quality-value string such as "text/html;q=0.9".
     */
    public static QualityValue parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip();
        int qIndex = trimmed.toLowerCase().indexOf(";q=");
        if (qIndex >= 0) {
            String val = trimmed.substring(0, qIndex).strip();
            String qStr = trimmed.substring(qIndex + 3).strip();
            double q = Double.parseDouble(qStr);
            return new QualityValue(val, q);
        }
        return new QualityValue(trimmed);
    }

    public String value() {
        return value;
    }

    public double quality() {
        return quality;
    }

    /**
     * Compares by quality in descending order (higher quality first).
     */
    @Override
    public int compareTo(QualityValue other) {
        return Double.compare(other.quality, this.quality);
    }

    @Override
    public String toString() {
        if (Double.compare(quality, 1.0) == 0) {
            return value;
        }
        return value + ";q=" + quality;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QualityValue other)) return false;
        return Double.compare(quality, other.quality) == 0
                && value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, quality);
    }
}
