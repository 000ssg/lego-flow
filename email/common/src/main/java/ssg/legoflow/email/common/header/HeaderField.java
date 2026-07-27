package ssg.legoflow.email.common.header;

import ssg.legoflow.email.common.encoding.EncodedWordCodec;

import java.util.Objects;

/**
 * A single header field consisting of a name and value.
 *
 * <p>Header names are case-insensitive per RFC 5322. Values may contain
 * RFC 2047 encoded words which are decoded on access.
 *
 * @since 1.0.0
 */
public final class HeaderField {

    private final String name;
    private final String rawValue;

    /**
     * Creates a header field with the given name and value.
     *
     * @param name     the header name (e.g., "Content-Type")
     * @param rawValue the raw header value (may contain encoded words or folding)
     */
    public HeaderField(String name, String rawValue) {
        this.name = Objects.requireNonNull(name, "Header name must not be null");
        this.rawValue = Objects.requireNonNull(rawValue, "Header value must not be null");
    }

    /**
     * Returns the header name.
     *
     * @return the header name
     */
    public String name() {
        return name;
    }

    /**
     * Returns the raw header value without decoding.
     *
     * @return the raw value
     */
    public String rawValue() {
        return rawValue;
    }

    /**
     * Returns the decoded header value with RFC 2047 encoded words resolved.
     *
     * @return the decoded value
     */
    public String decodedValue() {
        return EncodedWordCodec.decode(rawValue);
    }

    /**
     * Checks whether this header's name matches the given name (case-insensitive).
     *
     * @param otherName the name to compare
     * @return true if the names match ignoring case
     */
    public boolean nameEquals(String otherName) {
        return name.equalsIgnoreCase(otherName);
    }

    /**
     * Serializes this header field to wire format: {@code name: value}.
     *
     * @return the serialized header
     */
    public String toWireFormat() {
        return name + ": " + rawValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HeaderField other)) return false;
        return name.equalsIgnoreCase(other.name) && rawValue.equals(other.rawValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name.toLowerCase(), rawValue);
    }

    @Override
    public String toString() {
        return toWireFormat();
    }
}
