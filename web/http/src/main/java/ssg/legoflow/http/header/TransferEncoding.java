package ssg.legoflow.http.header;

import java.util.Objects;

/**
 * Represents HTTP transfer encoding values.
 */
public enum TransferEncoding {

    CHUNKED("chunked"),
    IDENTITY("identity");

    private final String value;

    TransferEncoding(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /**
     * Parses a transfer encoding string (case-insensitive).
     *
     * @throws IllegalArgumentException if the value is not a recognized transfer encoding
     */
    public static TransferEncoding parse(String input) {
        Objects.requireNonNull(input, "input must not be null");
        String trimmed = input.strip().toLowerCase();
        for (TransferEncoding encoding : values()) {
            if (encoding.value.equals(trimmed)) {
                return encoding;
            }
        }
        throw new IllegalArgumentException("Unknown transfer encoding: " + input);
    }

    @Override
    public String toString() {
        return value;
    }
}
