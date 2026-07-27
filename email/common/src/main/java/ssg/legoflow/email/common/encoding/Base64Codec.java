package ssg.legoflow.email.common.encoding;

import java.util.Base64;

/**
 * Base64 encode/decode per RFC 2045.
 *
 * <p>MIME Base64 wraps output at 76 characters per line with CRLF line endings.
 * Decoding is lenient and ignores whitespace.
 *
 * @since 1.0.0
 */
public final class Base64Codec {

    /** Maximum line length for MIME Base64 encoding (RFC 2045 section 6.8). */
    public static final int MIME_LINE_LENGTH = 76;

    private static final byte[] CRLF = {'\r', '\n'};

    private Base64Codec() {
    }

    /**
     * Encodes bytes to MIME Base64 with line wrapping at 76 characters.
     *
     * @param data the bytes to encode
     * @return Base64-encoded string with CRLF line breaks every 76 characters
     */
    public static String encode(byte[] data) {
        String raw = Base64.getEncoder().encodeToString(data);
        if (raw.length() <= MIME_LINE_LENGTH) {
            return raw;
        }
        var sb = new StringBuilder(raw.length() + (raw.length() / MIME_LINE_LENGTH) * 2);
        int offset = 0;
        while (offset < raw.length()) {
            int end = Math.min(offset + MIME_LINE_LENGTH, raw.length());
            sb.append(raw, offset, end);
            if (end < raw.length()) {
                sb.append("\r\n");
            }
            offset = end;
        }
        return sb.toString();
    }

    /**
     * Encodes bytes to raw Base64 without line wrapping.
     *
     * @param data the bytes to encode
     * @return Base64-encoded string without line breaks
     */
    public static String encodeRaw(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }

    /**
     * Decodes a MIME Base64 string to bytes, ignoring whitespace.
     *
     * @param encoded the Base64-encoded string (may contain CRLF, spaces)
     * @return decoded bytes
     * @throws IllegalArgumentException if the input contains invalid Base64 characters
     */
    public static byte[] decode(String encoded) {
        String cleaned = encoded.replaceAll("\\s+", "");
        return Base64.getDecoder().decode(cleaned);
    }

    /**
     * Encodes bytes to MIME Base64 and returns as byte array.
     *
     * @param data the bytes to encode
     * @return Base64-encoded bytes with CRLF line breaks
     */
    public static byte[] encodeToBytes(byte[] data) {
        return encode(data).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * Decodes a Base64 byte array to bytes, ignoring whitespace.
     *
     * @param encoded the Base64-encoded bytes
     * @return decoded bytes
     */
    public static byte[] decodeBytes(byte[] encoded) {
        return decode(new String(encoded, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
