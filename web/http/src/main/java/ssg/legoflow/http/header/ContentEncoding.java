package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents HTTP content encoding values.
 */
public enum ContentEncoding {

    GZIP("gzip"),
    DEFLATE("deflate"),
    IDENTITY("identity"),
    COMPRESS("compress"),
    BR("br");

    private final String value;

    ContentEncoding(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Parses a content encoding string (case-insensitive).
     *
     * @throws IllegalArgumentException if the value is not a recognized encoding
     */
    public static ContentEncoding parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip().toLowerCase();
        for (ContentEncoding encoding : values()) {
            if (encoding.value.equals(trimmed)) {
                return encoding;
            }
        }
        throw new IllegalArgumentException("Unknown content encoding: " + input);
    }

    @Override
    public String toString() {
        return value;
    }
}
