package ssg.legoflow.media.common.sdp;

import java.util.Objects;
import java.util.Optional;

/**
 * Generic SDP attribute ({@code a=} line).
 *
 * <p>Attributes may be property-style ({@code a=name}) or value-style
 * ({@code a=name:value}). This record preserves both forms.
 *
 * @param name  the attribute name (never null)
 * @param value the attribute value, or empty for property-style attributes
 * @since 0.1.0
 */
public record Attribute(String name, Optional<String> value) {

    /**
     * Creates an attribute with validation.
     *
     * @param name  the attribute name
     * @param value the optional attribute value
     */
    public Attribute {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
    }

    /**
     * Creates a property-style attribute (no value).
     *
     * @param name the attribute name
     * @return a property-style attribute
     */
    public static Attribute property(String name) {
        return new Attribute(name, Optional.empty());
    }

    /**
     * Creates a value-style attribute.
     *
     * @param name  the attribute name
     * @param value the attribute value
     * @return a value-style attribute
     */
    public static Attribute of(String name, String value) {
        return new Attribute(name, Optional.of(value));
    }

    /**
     * Parses an attribute from an {@code a=} line value (after the {@code a=} prefix).
     *
     * @param line the attribute text (e.g., "recvonly" or "rtpmap:96 H264/90000")
     * @return the parsed attribute
     */
    public static Attribute parse(String line) {
        int colon = line.indexOf(':');
        if (colon >= 0) {
            return of(line.substring(0, colon), line.substring(colon + 1));
        }
        return property(line);
    }

    /**
     * Formats this attribute for inclusion in an SDP document (without the {@code a=} prefix).
     *
     * @return the formatted attribute string
     */
    public String format() {
        return value.map(v -> name + ":" + v).orElse(name);
    }

    @Override
    public String toString() {
        return "a=" + format();
    }
}
