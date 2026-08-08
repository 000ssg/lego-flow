package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.header.ParameterParser;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Content-Disposition header parsing per RFC 2183.
 *
 * <p>Represents the disposition type (inline, attachment) and parameters
 * such as filename, creation-date, modification-date, and size.
 *
 * @since 0.1.0
 */
public final class ContentDisposition {

    /** Inline disposition — display in message body. */
    public static final String INLINE = "inline";

    /** Attachment disposition — separate from message body. */
    public static final String ATTACHMENT = "attachment";

    private final String type;
    private final Map<String, String> parameters;

    /**
     * Creates a ContentDisposition with type and parameters.
     *
     * @param type       the disposition type (e.g., "attachment")
     * @param parameters the parameters (e.g., filename)
     */
    public ContentDisposition(String type, Map<String, String> parameters) {
        this.type = Objects.requireNonNull(type, "Disposition type must not be null")
                .trim().toLowerCase();
        this.parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Creates a ContentDisposition without parameters.
     *
     * @param type the disposition type
     */
    public ContentDisposition(String type) {
        this(type, Map.of());
    }

    /**
     * Parses a Content-Disposition header value.
     *
     * @param headerValue the header value (e.g., "attachment; filename=\"doc.pdf\"")
     * @return the parsed ContentDisposition
     */
    public static ContentDisposition parse(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return new ContentDisposition(INLINE);
        }

        String trimmed = headerValue.trim();
        Map<String, String> params = ParameterParser.parse(trimmed);

        int semiPos = trimmed.indexOf(';');
        String type = (semiPos >= 0 ? trimmed.substring(0, semiPos) : trimmed).trim().toLowerCase();

        return new ContentDisposition(type, params);
    }

    /**
     * Creates an inline disposition.
     *
     * @return an inline ContentDisposition
     */
    public static ContentDisposition inline() {
        return new ContentDisposition(INLINE);
    }

    /**
     * Creates an attachment disposition with a filename.
     *
     * @param filename the filename
     * @return an attachment ContentDisposition
     */
    public static ContentDisposition attachment(String filename) {
        var params = new LinkedHashMap<String, String>();
        if (filename != null) {
            params.put("filename", filename);
        }
        return new ContentDisposition(ATTACHMENT, params);
    }

    /**
     * Returns the disposition type.
     *
     * @return the type (e.g., "attachment", "inline")
     */
    public String type() {
        return type;
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
     * Returns the filename parameter.
     *
     * @return the filename, or null if not present
     */
    public String filename() {
        return parameters.get("filename");
    }

    /**
     * Returns the size parameter as a long.
     *
     * @return the size, or -1 if not present
     */
    public long size() {
        String sizeStr = parameters.get("size");
        if (sizeStr != null) {
            try {
                return Long.parseLong(sizeStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /**
     * Checks whether this is an inline disposition.
     *
     * @return true if inline
     */
    public boolean isInline() {
        return INLINE.equals(type);
    }

    /**
     * Checks whether this is an attachment disposition.
     *
     * @return true if attachment
     */
    public boolean isAttachment() {
        return ATTACHMENT.equals(type);
    }

    /**
     * Serializes this ContentDisposition to a header value string.
     *
     * @return the serialized value
     */
    public String toHeaderValue() {
        var sb = new StringBuilder(type);
        sb.append(ParameterParser.serialize(parameters));
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentDisposition other)) return false;
        return type.equals(other.type) && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, parameters);
    }

    @Override
    public String toString() {
        return toHeaderValue();
    }
}
