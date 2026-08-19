package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.header.ParameterParser;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
/**
 * Content-Type header parsing per RFC 2045.
 *
 * <p>Represents the media type ({@code type/subtype}) and its parameters
 * (charset, boundary, name, etc.).
 *
 * @since 0.1.0
 */
public final class ContentType {

    /** Default Content-Type for messages (RFC 2045 section 5.2). */
    public static final ContentType DEFAULT = new ContentType("text", "plain",
            Map.of("charset", "us-ascii"));

    /** Common content types. */
    public static final ContentType TEXT_PLAIN = new ContentType("text", "plain", Map.of());
    public static final ContentType TEXT_HTML = new ContentType("text", "html", Map.of());
    public static final ContentType MESSAGE_RFC822 = new ContentType("message", "rfc822", Map.of());
    public static final ContentType APPLICATION_OCTET_STREAM =
            new ContentType("application", "octet-stream", Map.of());

    private final String type;
    private final String subtype;
    private final Map<String, String> parameters;

    /**
     * Creates a ContentType with type, subtype, and parameters.
     *
     * @param type       the primary type (e.g., "text")
     * @param subtype    the subtype (e.g., "plain")
     * @param parameters the parameters (e.g., charset, boundary)
     */
    public ContentType(String type, String subtype, Map<String, String> parameters) {
        this.type = Objects.requireNonNull(type, "Type must not be null").toLowerCase();
        this.subtype = Objects.requireNonNull(subtype, "Subtype must not be null").toLowerCase();
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Creates a ContentType without parameters.
     *
     * @param type    the primary type
     * @param subtype the subtype
     */
    public ContentType(String type, String subtype) {
        this(type, subtype, Map.of());
    }

    /**
     * Parses a Content-Type header value.
     *
     * @param headerValue the header value (e.g., "text/plain; charset=utf-8")
     * @return the parsed ContentType
     * @throws IllegalArgumentException if the value is malformed
     */
    public static ContentType parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return DEFAULT;
        }

        String trimmed = headerValue.trim();
        Map<String, String> params = ParameterParser.parse(trimmed);

        // Extract type/subtype from the first segment
        int semiPos = trimmed.indexOf(';');
        String mediaType = (semiPos >= 0 ? trimmed.substring(0, semiPos) : trimmed).trim();

        int slashPos = mediaType.indexOf('/');
        if (slashPos < 0) {
            throw new IllegalArgumentException("Missing subtype in Content-Type: " + headerValue);
        }

        String type = mediaType.substring(0, slashPos).trim().toLowerCase();
        String subtype = mediaType.substring(slashPos + 1).trim().toLowerCase();

        return new ContentType(type, subtype, params);
    }

    /**
     * Returns the primary type (e.g., "text").
     *
     * @return the primary type
     */
    public String type() {
        return type;
    }

    /**
     * Returns the subtype (e.g., "plain").
     *
     * @return the subtype
     */
    public String subtype() {
        return subtype;
    }

    /**
     * Returns the full media type (e.g., "text/plain").
     *
     * @return the media type
     */
    public String mediaType() {
        return type + "/" + subtype;
    }

    /**
     * Returns the parameters map.
     *
     * @return unmodifiable map of parameters
     */
    public Map<String, String> parameters() {
        return parameters;
    }

    /**
     * Returns a parameter value by name (case-insensitive).
     *
     * @param name the parameter name
     * @return the value, or null if not present
     */
    public String parameter(String name) {
        return parameters.get(name.toLowerCase());
    }

    /**
     * Returns the charset parameter, defaulting to US-ASCII for text types.
     *
     * @return the charset
     */
    public Charset charset() {
        String charsetName = parameter("charset");
        if (charsetName != null) {
            try {
                return ssg.legoflow.email.common.encoding.CharsetUtils.forName(charsetName);
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
        if ("text".equals(type)) {
            return StandardCharsets.US_ASCII;
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Returns the boundary parameter (for multipart types).
     *
     * @return the boundary, or null if not present
     */
    public String boundary() {
        return parameter("boundary");
    }

    /**
     * Returns the name parameter (commonly used for filenames).
     *
     * @return the name, or null if not present
     */
    public String name() {
        return parameter("name");
    }

    /**
     * Checks whether this is a text type.
     *
     * @return true if the primary type is "text"
     */
    public boolean isText() {
        return "text".equals(type);
    }

    /**
     * Checks whether this is a multipart type.
     *
     * @return true if the primary type is "multipart"
     */
    public boolean isMultipart() {
        return "multipart".equals(type);
    }

    /**
     * Checks whether this is a message type.
     *
     * @return true if the primary type is "message"
     */
    public boolean isMessage() {
        return "message".equals(type);
    }

    /**
     * Creates a new ContentType with an additional or replaced parameter.
     *
     * @param name  the parameter name
     * @param value the parameter value
     * @return a new ContentType with the parameter set
     */
    public ContentType withParameter(String name, String value) {
        var newParams = new LinkedHashMap<>(parameters);
        newParams.put(name.toLowerCase(), value);
        return new ContentType(type, subtype, newParams);
    }

    /**
     * Serializes this ContentType to a header value string.
     *
     * @return the serialized value (e.g., "text/plain; charset=utf-8")
     */
    public String toHeaderValue() {
        var sb = new StringBuilder();
        sb.append(type).append("/").append(subtype);
        sb.append(ParameterParser.serialize(parameters));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentType other)) return false;
        return type.equals(other.type) && subtype.equals(other.subtype)
                && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, subtype, parameters);
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }
}
