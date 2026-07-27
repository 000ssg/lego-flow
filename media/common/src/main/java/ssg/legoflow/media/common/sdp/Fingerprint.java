package ssg.legoflow.media.common.sdp;

import java.util.Objects;

/**
 * Parsed DTLS fingerprint attribute ({@code a=fingerprint:}) as defined in RFC 4572.
 *
 * <p>Format: {@code a=fingerprint:<hash-func> <fingerprint>}
 *
 * @param hashFunction the hash function name (e.g., "sha-256", "sha-1")
 * @param hashValue    the fingerprint hash value (colon-separated hex octets)
 * @since 1.0.0
 */
public record Fingerprint(String hashFunction, String hashValue) {

    /**
     * Creates a fingerprint with validation.
     */
    public Fingerprint {
        Objects.requireNonNull(hashFunction, "hashFunction");
        Objects.requireNonNull(hashValue, "hashValue");
    }

    /**
     * Parses a fingerprint from the value part of an {@code a=fingerprint:} attribute.
     *
     * @param value the fingerprint value (e.g., "sha-256 AB:CD:EF:...")
     * @return the parsed fingerprint
     * @throws IllegalArgumentException if the format is invalid
     */
    public static Fingerprint parse(String value) {
        int space = value.indexOf(' ');
        if (space < 0) {
            throw new IllegalArgumentException("Invalid fingerprint, expected space: " + value);
        }
        return new Fingerprint(value.substring(0, space), value.substring(space + 1).trim());
    }

    /**
     * Formats this fingerprint for use as the value of an {@code a=fingerprint:} attribute.
     *
     * @return the formatted fingerprint value
     */
    public String format() {
        return hashFunction + " " + hashValue;
    }

    @Override
    public String toString() {
        return "a=fingerprint:" + format();
    }
}
