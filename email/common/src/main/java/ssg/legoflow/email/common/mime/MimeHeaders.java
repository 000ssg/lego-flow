package ssg.legoflow.email.common.mime;

import ssg.legoflow.email.common.header.HeaderField;

import java.util.*;

/**
 * Case-insensitive header map with support for header folding/unfolding.
 *
 * <p>Headers are stored in insertion order. Multiple headers with the same name
 * are allowed (e.g., multiple Received headers). Lookup is case-insensitive.
 *
 * <p>Supports RFC 5322 header folding (long lines wrapped with CRLF + whitespace)
 * and unfolding (removing the CRLF and leading whitespace).
 *
 * @since 0.1.0
 */
public final class MimeHeaders {

    /** Maximum recommended line length for headers (RFC 5322 section 2.1.1). */
    public static final int MAX_LINE_LENGTH = 78;

    /** Hard limit on line length (RFC 5322 section 2.1.1). */
    public static final int HARD_LINE_LIMIT = 998;

    private final List<HeaderField> fields;

    /**
     * Creates an empty MimeHeaders.
     */
    public MimeHeaders() {
        this.fields = new ArrayList<>();
    }

    /**
     * Creates a MimeHeaders from a list of fields.
     *
     * @param fields the header fields
     */
    public MimeHeaders(List<HeaderField> fields) {
        this.fields = new ArrayList<>(fields);
    }

    /**
     * Adds a header field.
     *
     * @param name  the header name
     * @param value the header value
     */
    public void add(String name, String value) {
        fields.add(new HeaderField(name, value));
    }

    /**
     * Sets a header field, replacing any existing fields with the same name.
     *
     * @param name  the header name
     * @param value the header value
     */
    public void set(String name, String value) {
        fields.removeIf(f -> f.nameEquals(name));
        fields.add(new HeaderField(name, value));
    }

    /**
     * Returns the first value for the given header name (case-insensitive).
     *
     * @param name the header name
     * @return the value, or null if not present
     */
    public String get(String name) {
        for (HeaderField field : fields) {
            if (field.nameEquals(name)) {
                return field.rawValue();
            }
        }
        return null;
    }

    /**
     * Returns the first decoded value for the given header name.
     *
     * @param name the header name
     * @return the decoded value, or null if not present
     */
    public String getDecoded(String name) {
        for (HeaderField field : fields) {
            if (field.nameEquals(name)) {
                return field.decodedValue();
            }
        }
        return null;
    }

    /**
     * Returns all values for the given header name (case-insensitive).
     *
     * @param name the header name
     * @return list of values (empty if none found)
     */
    public List<String> getAll(String name) {
        var result = new ArrayList<String>();
        for (HeaderField field : fields) {
            if (field.nameEquals(name)) {
                result.add(field.rawValue());
            }
        }
        return result;
    }

    /**
     * Checks whether a header with the given name exists.
     *
     * @param name the header name
     * @return true if present
     */
    public boolean contains(String name) {
        return fields.stream().anyMatch(f -> f.nameEquals(name));
    }

    /**
     * Removes all headers with the given name.
     *
     * @param name the header name
     */
    public void remove(String name) {
        fields.removeIf(f -> f.nameEquals(name));
    }

    /**
     * Returns all header fields in insertion order.
     *
     * @return unmodifiable list of header fields
     */
    public List<HeaderField> fields() {
        return Collections.unmodifiableList(fields);
    }

    /**
     * Returns the number of header fields.
     *
     * @return the count
     */
    public int size() {
        return fields.size();
    }

    /**
     * Returns whether this headers collection is empty.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return fields.isEmpty();
    }

    /**
     * Parses the Content-Type header.
     *
     * @return the parsed ContentType, or the default if not present
     */
    public ContentType contentType() {
        String value = get("Content-Type");
        return value != null ? ContentType.parse(value) : ContentType.DEFAULT;
    }

    /**
     * Parses the Content-Disposition header.
     *
     * @return the parsed ContentDisposition, or null if not present
     */
    public ContentDisposition contentDisposition() {
        String value = get("Content-Disposition");
        return value != null ? ContentDisposition.parse(value) : null;
    }

    /**
     * Parses the Content-Transfer-Encoding header.
     *
     * @return the parsed encoding, or SEVEN_BIT if not present
     */
    public ContentTransferEncoding contentTransferEncoding() {
        String value = get("Content-Transfer-Encoding");
        return value != null ? ContentTransferEncoding.parse(value) : ContentTransferEncoding.SEVEN_BIT;
    }

    /**
     * Folds a header line at word boundaries if it exceeds the recommended length.
     *
     * @param headerLine the header line (name: value)
     * @return the folded header line
     */
    public static String fold(String headerLine) {
        if (headerLine.length() <= MAX_LINE_LENGTH) {
            return headerLine;
        }
        var sb = new StringBuilder();
        int lineStart = 0;
        int lastSpace = -1;

        for (int i = 0; i < headerLine.length(); i++) {
            char c = headerLine.charAt(i);
            if (c == ' ' || c == '\t') {
                lastSpace = i;
            }
            if (i - lineStart >= MAX_LINE_LENGTH && lastSpace > lineStart) {
                sb.append(headerLine, lineStart, lastSpace);
                sb.append("\r\n ");
                lineStart = lastSpace + 1;
                lastSpace = -1;
            }
        }
        sb.append(headerLine, lineStart, headerLine.length());
        return sb.toString();
    }

    /**
     * Unfolds a header value by removing CRLF followed by whitespace.
     *
     * @param value the potentially folded header value
     * @return the unfolded value
     */
    public static String unfold(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("\r\n[ \t]", " ").replaceAll("\n[ \t]", " ");
    }

    /**
     * Serializes all headers to wire format with CRLF line endings.
     *
     * @return the serialized headers
     */
    public String toWireFormat() {
        var sb = new StringBuilder();
        for (HeaderField field : fields) {
            sb.append(fold(field.toWireFormat()));
            sb.append("\r\n");
        }
        return sb.toString();
    }

    /**
     * Parses headers from a string (lines separated by CRLF or LF).
     *
     * @param headerBlock the raw header block
     * @return the parsed MimeHeaders
     */
    public static MimeHeaders parse(String headerBlock) {
        var headers = new MimeHeaders();
        if (headerBlock == null || headerBlock.isEmpty()) {
            return headers;
        }

        // Split into lines, handling both CRLF and LF
        String[] lines = headerBlock.split("\r?\n");

        String currentName = null;
        var currentValue = new StringBuilder();

        for (String line : lines) {
            if (line.isEmpty()) {
                break; // End of headers
            }
            if ((line.charAt(0) == ' ' || line.charAt(0) == '\t') && currentName != null) {
                // Continuation line — unfold
                currentValue.append(" ").append(line.trim());
            } else {
                // New header — save previous if exists
                if (currentName != null) {
                    headers.add(currentName, currentValue.toString());
                }
                int colonPos = line.indexOf(':');
                if (colonPos > 0) {
                    currentName = line.substring(0, colonPos);
                    currentValue = new StringBuilder(line.substring(colonPos + 1).trim());
                } else {
                    currentName = null;
                }
            }
        }
        // Save last header
        if (currentName != null) {
            headers.add(currentName, currentValue.toString());
        }

        return headers;
    }

    @Override
    public String toString() {
        return "MimeHeaders{" + fields.size() + " fields}";
    }
}
