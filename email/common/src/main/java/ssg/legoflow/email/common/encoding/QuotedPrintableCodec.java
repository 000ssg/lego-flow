package ssg.legoflow.email.common.encoding;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Quoted-Printable encode/decode per RFC 2045 section 6.7.
 *
 * <p>Encodes non-printable and non-ASCII bytes as {@code =XX} hex sequences.
 * Lines are limited to 76 characters with soft line breaks ({@code =\r\n}).
 *
 * @since 0.1.0
 */
public final class QuotedPrintableCodec {

    /** Maximum line length for QP encoding (RFC 2045 section 6.7). */
    public static final int MAX_LINE_LENGTH = 76;

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private QuotedPrintableCodec() {
    }

    /**
     * Encodes bytes to Quoted-Printable with soft line breaks.
     *
     * @param data the bytes to encode
     * @return QP-encoded string
     */
    public static String encode(byte[] data) {
        var sb = new StringBuilder(data.length * 2);
        int lineLen = 0;

        for (int i = 0; i < data.length; i++) {
            int b = data[i] & 0xFF;

            // Handle CRLF pass-through
            if (b == '\r' && i + 1 < data.length && (data[i + 1] & 0xFF) == '\n') {
                sb.append("\r\n");
                lineLen = 0;
                i++; // skip the \n
                continue;
            }

            String encoded;
            if (b == '\t' || (b >= 32 && b <= 126 && b != '=')) {
                // Printable ASCII (except '=') and TAB pass through
                // But trailing whitespace before CRLF or end must be encoded
                if ((b == ' ' || b == '\t') && isTrailingWhitespace(data, i)) {
                    encoded = encodeHex(b);
                } else {
                    encoded = String.valueOf((char) b);
                }
            } else if (b == '\n') {
                // Bare LF — encode
                encoded = encodeHex(b);
            } else if (b == '\r') {
                // Bare CR — encode
                encoded = encodeHex(b);
            } else {
                encoded = encodeHex(b);
            }

            // Check if we need a soft line break
            if (lineLen + encoded.length() > MAX_LINE_LENGTH - 1) {
                sb.append("=\r\n");
                lineLen = 0;
            }

            sb.append(encoded);
            lineLen += encoded.length();
        }

        return sb.toString();
    }

    /**
     * Decodes a Quoted-Printable string to bytes.
     *
     * @param encoded the QP-encoded string
     * @return decoded bytes
     * @throws IllegalArgumentException if the input contains invalid QP sequences
     */
    public static byte[] decode(String encoded) {
        var out = new ByteArrayOutputStream(encoded.length());
        int i = 0;
        while (i < encoded.length()) {
            char c = encoded.charAt(i);
            if (c == '=') {
                if (i + 1 < encoded.length() && encoded.charAt(i + 1) == '\r'
                        && i + 2 < encoded.length() && encoded.charAt(i + 2) == '\n') {
                    // Soft line break — skip
                    i += 3;
                } else if (i + 1 < encoded.length() && encoded.charAt(i + 1) == '\n') {
                    // Soft line break (bare LF) — lenient
                    i += 2;
                } else if (i + 2 < encoded.length()) {
                    int hi = Character.digit(encoded.charAt(i + 1), 16);
                    int lo = Character.digit(encoded.charAt(i + 2), 16);
                    if (hi < 0 || lo < 0) {
                        throw new IllegalArgumentException(
                                "Invalid QP sequence at position " + i + ": ="
                                        + encoded.charAt(i + 1) + encoded.charAt(i + 2));
                    }
                    out.write((hi << 4) | lo);
                    i += 3;
                } else {
                    throw new IllegalArgumentException(
                            "Truncated QP sequence at position " + i);
                }
            } else {
                out.write((byte) c);
                i++;
            }
        }
        return out.toByteArray();
    }

    /**
     * Encodes a string to Quoted-Printable using the specified charset.
     *
     * @param text    the text to encode
     * @param charset the charset to use for encoding
     * @return QP-encoded string
     */
    public static String encode(String text, java.nio.charset.Charset charset) {
        return encode(text.getBytes(charset));
    }

    private static String encodeHex(int b) {
        return "=" + HEX[(b >> 4) & 0x0F] + HEX[b & 0x0F];
    }

    private static boolean isTrailingWhitespace(byte[] data, int pos) {
        for (int i = pos + 1; i < data.length; i++) {
            int b = data[i] & 0xFF;
            if (b == '\r' || b == '\n') {
                return true;
            }
            if (b != ' ' && b != '\t') {
                return false;
            }
        }
        // Whitespace at end of data
        return true;
    }
}
