package ssg.legoflow.network.syslog.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Syslog structured data element as defined in RFC 5424 Section 6.3.
 *
 * <p>A structured data element consists of an SD-ID and zero or more
 * SD-PARAMs (key-value pairs). The format is:
 * {@code [sdID param1="value1" param2="value2"]}.
 *
 * <p>Well-known SD-IDs include:
 * <ul>
 *   <li>{@code timeQuality} — time quality parameters</li>
 *   <li>{@code origin} — originating information</li>
 *   <li>{@code meta} — meta information</li>
 * </ul>
 *
 * @param id     the SD-ID (must not contain '=', ']', '"', or space)
 * @param params the SD-PARAMs as an ordered map of name to value
 * @since 0.1.0
 */
public record StructuredData(String id, Map<String, String> params) {

    /** Well-known SD-ID for time quality information. */
    public static final String TIME_QUALITY = "timeQuality";
    /** Well-known SD-ID for originating information. */
    public static final String ORIGIN = "origin";
    /** Well-known SD-ID for meta information. */
    public static final String META = "meta";

    /**
     * Creates a structured data element with validation.
     *
     * @param id     the SD-ID
     * @param params the parameters
     */
    public StructuredData {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("SD-ID must not be null or empty");
        }
        if (id.contains("=") || id.contains("]") || id.contains("\"") || id.contains(" ")) {
            throw new IllegalArgumentException("SD-ID contains invalid characters: " + id);
        }
        params = params != null ? Map.copyOf(params) : Map.of();
    }

    /**
     * Creates a structured data element with no parameters.
     *
     * @param id the SD-ID
     * @return the structured data element
     */
    public static StructuredData of(String id) {
        return new StructuredData(id, Map.of());
    }

    /**
     * Creates a structured data element with parameters.
     *
     * @param id     the SD-ID
     * @param params the parameters
     * @return the structured data element
     */
    public static StructuredData of(String id, Map<String, String> params) {
        return new StructuredData(id, params);
    }

    /**
     * Returns a new builder for constructing structured data.
     *
     * @param id the SD-ID
     * @return a new builder
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Encodes this structured data element to its string representation.
     *
     * <p>Format: {@code [sdID param1="value1" param2="value2"]}
     * Values are escaped per RFC 5424: {@code \}, {@code "}, and {@code ]}
     * are prefixed with {@code \}.
     *
     * @return the encoded string
     */
    public String encode() {
        var sb = new StringBuilder();
        sb.append('[').append(id);
        for (var entry : params.entrySet()) {
            sb.append(' ').append(entry.getKey()).append("=\"");
            sb.append(escapeValue(entry.getValue()));
            sb.append('"');
        }
        sb.append(']');
        return sb.toString();
    }

    private static String escapeValue(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("]", "\\]");
    }

    /**
     * Builder for constructing structured data incrementally.
     */
    public static final class Builder {
        private final String id;
        private final Map<String, String> params = new LinkedHashMap<>();

        private Builder(String id) {
            this.id = id;
        }

        /**
         * Adds a parameter.
         *
         * @param name  the parameter name
         * @param value the parameter value
         * @return this builder
         */
        public Builder param(String name, String value) {
            params.put(name, value);
            return this;
        }

        /**
         * Builds the structured data element.
         *
         * @return the structured data element
         */
        public StructuredData build() {
            return new StructuredData(id, params);
        }
    }
}
