package ssg.legoflow.email.imap.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * IMAP literal string handling per RFC 9051.
 *
 * <p>Literals are used to transmit strings that may contain special characters.
 * The format is {@code {N}\r\n} followed by exactly N octets of data.
 * Non-synchronizing literals use {@code {N+}\r\n} and do not require
 * server confirmation before sending data.
 *
 * @since 0.1.0
 */
public final class ImapLiteral {

    private final byte[] data;
    private final boolean nonSynchronizing;

    /**
     * Creates a new literal with the given data.
     *
     * @param data             the literal data
     * @param nonSynchronizing true for non-synchronizing literal ({N+})
     */
    public ImapLiteral(byte[] data, boolean nonSynchronizing) {
        this.data = Objects.requireNonNull(data).clone();
        this.nonSynchronizing = nonSynchronizing;
    }

    /**
     * Creates a synchronizing literal from a string.
     *
     * @param text the text
     * @return the literal
     */
    public static ImapLiteral of(String text) {
        return new ImapLiteral(text.getBytes(StandardCharsets.UTF_8), false);
    }

    /**
     * Creates a non-synchronizing literal from a string.
     *
     * @param text the text
     * @return the literal
     */
    public static ImapLiteral nonSync(String text) {
        return new ImapLiteral(text.getBytes(StandardCharsets.UTF_8), true);
    }

    /**
     * Returns the literal data.
     *
     * @return a copy of the data bytes
     */
    public byte[] data() {
        return data.clone();
    }

    /**
     * Returns the literal size in octets.
     *
     * @return the size
     */
    public int size() {
        return data.length;
    }

    /**
     * Returns whether this is a non-synchronizing literal.
     *
     * @return true if non-synchronizing
     */
    public boolean isNonSynchronizing() {
        return nonSynchronizing;
    }

    /**
     * Returns the literal header (e.g., "{5}" or "{5+}").
     *
     * @return the literal header string
     */
    public String header() {
        return nonSynchronizing
                ? "{" + data.length + "+}"
                : "{" + data.length + "}";
    }

    /**
     * Returns the literal data as a UTF-8 string.
     *
     * @return the string representation
     */
    public String asString() {
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * Parses a literal header to extract the size and synchronizing mode.
     *
     * @param header the header string (e.g., "{123}" or "{123+}")
     * @return a two-element array: [size, nonSynchronizing (0 or 1)]
     * @throws IllegalArgumentException if the header is malformed
     */
    public static long[] parseLiteralHeader(String header) {
        if (!header.startsWith("{") || !header.endsWith("}")) {
            throw new IllegalArgumentException("Invalid literal header: " + header);
        }
        String inner = header.substring(1, header.length() - 1);
        boolean nonSync = inner.endsWith("+");
        if (nonSync) {
            inner = inner.substring(0, inner.length() - 1);
        }
        long size = Long.parseLong(inner);
        return new long[]{size, nonSync ? 1 : 0};
    }

    @Override
    public String toString() {
        return header() + " (" + data.length + " bytes)";
    }
}
