package ssg.legoflow.media.common.sdp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed format parameters attribute ({@code a=fmtp:}).
 *
 * <p>Format: {@code a=fmtp:<format> <format specific parameters>}
 * Parameters are typically semicolon-separated key=value pairs.
 *
 * @param payloadType the format (payload type number)
 * @param parameters  the parsed parameters as key-value pairs
 * @param rawValue    the raw parameter string (preserved for unknown formats)
 * @since 1.0.0
 */
public record FormatParameters(int payloadType, Map<String, String> parameters, String rawValue) {

    /**
     * Creates format parameters with validation.
     */
    public FormatParameters {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(rawValue, "rawValue");
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Parses format parameters from the value part of an {@code a=fmtp:} attribute.
     *
     * @param value the fmtp value (e.g., "96 profile-level-id=42e01f;packetization-mode=1")
     * @return the parsed format parameters
     * @throws IllegalArgumentException if the format is invalid
     */
    public static FormatParameters parse(String value) {
        int space = value.indexOf(' ');
        if (space < 0) {
            return new FormatParameters(Integer.parseInt(value.trim()), Map.of(), "");
        }
        int pt = Integer.parseInt(value.substring(0, space).trim());
        String params = value.substring(space + 1).trim();
        Map<String, String> map = new LinkedHashMap<>();
        for (String param : params.split(";")) {
            String trimmed = param.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq >= 0) {
                map.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            } else {
                map.put(trimmed, "");
            }
        }
        return new FormatParameters(pt, map, params);
    }

    /**
     * Formats this for use as the value of an {@code a=fmtp:} attribute.
     *
     * @return the formatted fmtp value
     */
    public String format() {
        if (rawValue.isEmpty()) {
            return String.valueOf(payloadType);
        }
        return payloadType + " " + rawValue;
    }

    @Override
    public String toString() {
        return "a=fmtp:" + format();
    }
}
