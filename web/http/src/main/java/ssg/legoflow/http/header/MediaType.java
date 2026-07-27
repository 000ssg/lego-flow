package ssg.legoflow.http.header;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents an HTTP media type (MIME type) such as "text/html;charset=utf-8".
 */
public record MediaType(String type, String subtype, Map<String, String> parameters) {

    public static final MediaType TEXT_PLAIN = new MediaType("text", "plain");
    public static final MediaType TEXT_HTML = new MediaType("text", "html");
    public static final MediaType TEXT_XML = new MediaType("text", "xml");
    public static final MediaType APPLICATION_JSON = new MediaType("application", "json");
    public static final MediaType APPLICATION_XML = new MediaType("application", "xml");
    public static final MediaType APPLICATION_OCTET_STREAM = new MediaType("application", "octet-stream");
    public static final MediaType APPLICATION_FORM_URLENCODED = new MediaType("application", "x-www-form-urlencoded");
    public static final MediaType MULTIPART_FORM_DATA = new MediaType("multipart", "form-data");

    public MediaType {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(subtype, "subtype must not be null");
        parameters = parameters == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    public MediaType(String type, String subtype) {
        this(type, subtype, Map.of());
    }

    /**
     * Parses a media type string such as "text/html;charset=utf-8".
     */
    public static MediaType parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip();

        String[] parts = trimmed.split(";");
        String[] typeParts = parts[0].strip().split("/", 2);
        if (typeParts.length != 2) {
            throw new IllegalArgumentException("Invalid media type: " + input);
        }

        String type = typeParts[0].strip().toLowerCase();
        String subtype = typeParts[1].strip().toLowerCase();

        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String param = parts[i].strip();
            int eqIndex = param.indexOf('=');
            if (eqIndex > 0) {
                String key = param.substring(0, eqIndex).strip().toLowerCase();
                String value = param.substring(eqIndex + 1).strip();
                params.put(key, value);
            }
        }

        return new MediaType(type, subtype, params);
    }

    /**
     * Checks whether this media type matches the given other media type,
     * supporting wildcard matching with &#42;/&#42; and type/&#42;.
     */
    public boolean matches(MediaType other) {
        Objects.requireNonNull(other, "other must not be null");

        if ("*".equals(this.type) || "*".equals(other.type)) {
            return true;
        }
        if (!this.type.equalsIgnoreCase(other.type)) {
            return false;
        }
        if ("*".equals(this.subtype) || "*".equals(other.subtype)) {
            return true;
        }
        return this.subtype.equalsIgnoreCase(other.subtype);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append(type).append('/').append(subtype);
        for (var entry : parameters.entrySet()) {
            sb.append(';').append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }
}
