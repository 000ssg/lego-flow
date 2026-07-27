package ssg.legoflow.email.common.encoding;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 2047 encoded-word codec for non-ASCII text in headers.
 *
 * <p>Format: {@code =?charset?encoding?encoded-text?=}
 * where encoding is {@code B} (Base64) or {@code Q} (Q-encoding, a variant of QP).
 *
 * @since 1.0.0
 */
public final class EncodedWordCodec {

    /** Maximum length of an encoded word (RFC 2047 section 2). */
    public static final int MAX_ENCODED_WORD_LENGTH = 75;

    private static final Pattern ENCODED_WORD_PATTERN =
            Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");

    // Pattern for whitespace between adjacent encoded words (should be ignored per RFC 2047)
    private static final Pattern ADJACENT_ENCODED_WORDS =
            Pattern.compile("\\?=\\s+=\\?");

    private EncodedWordCodec() {
    }

    /**
     * Encodes text as an RFC 2047 encoded word using Base64 encoding.
     *
     * @param text    the text to encode
     * @param charset the charset for encoding
     * @return the encoded word string
     */
    public static String encodeBase64(String text, Charset charset) {
        byte[] bytes = text.getBytes(charset);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return "=?" + charset.name() + "?B?" + encoded + "?=";
    }

    /**
     * Encodes text as an RFC 2047 encoded word using Q encoding.
     *
     * @param text    the text to encode
     * @param charset the charset for encoding
     * @return the encoded word string
     */
    public static String encodeQ(String text, Charset charset) {
        byte[] bytes = text.getBytes(charset);
        var sb = new StringBuilder();
        sb.append("=?").append(charset.name()).append("?Q?");
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (c == ' ') {
                sb.append('_');
            } else if (c == '_' || c == '?' || c == '=' || c < 33 || c > 126) {
                sb.append('=');
                sb.append(Character.toUpperCase(Character.forDigit((c >> 4) & 0x0F, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0x0F, 16)));
            } else {
                sb.append((char) c);
            }
        }
        sb.append("?=");
        return sb.toString();
    }

    /**
     * Encodes text as an RFC 2047 encoded word using Base64 with UTF-8.
     *
     * @param text the text to encode
     * @return the encoded word string
     */
    public static String encode(String text) {
        return encodeBase64(text, StandardCharsets.UTF_8);
    }

    /**
     * Decodes an RFC 2047 encoded header value.
     *
     * <p>Handles multiple encoded words, decoding each separately and concatenating.
     * Whitespace between adjacent encoded words is ignored per RFC 2047 section 6.2.
     *
     * @param header the header value potentially containing encoded words
     * @return the decoded string
     */
    public static String decode(String header) {
        if (header == null || !header.contains("=?")) {
            return header;
        }

        // Remove whitespace between adjacent encoded words
        String normalized = header;
        Matcher adjMatcher = ADJACENT_ENCODED_WORDS.matcher(normalized);
        normalized = adjMatcher.replaceAll("?= =?");
        // Now decode — but we keep the single space marker to know they were adjacent
        // Actually, RFC 2047 says whitespace between adjacent encoded words is removed
        // We handle this by tracking positions

        var result = new StringBuilder();
        Matcher matcher = ENCODED_WORD_PATTERN.matcher(header);
        int lastEnd = 0;
        boolean lastWasEncodedWord = false;

        while (matcher.find()) {
            int start = matcher.start();
            String between = header.substring(lastEnd, start);

            // If both previous and current are encoded words, and the gap is only whitespace,
            // ignore the whitespace (RFC 2047 section 6.2)
            if (lastWasEncodedWord && between.isBlank()) {
                // Skip whitespace between adjacent encoded words
            } else {
                result.append(between);
            }

            String charsetName = matcher.group(1);
            String encoding = matcher.group(2).toUpperCase();
            String encodedText = matcher.group(3);

            Charset charset = CharsetUtils.forName(charsetName);
            byte[] decoded;

            if ("B".equals(encoding)) {
                decoded = Base64.getDecoder().decode(encodedText);
            } else {
                decoded = decodeQ(encodedText);
            }

            result.append(new String(decoded, charset));
            lastEnd = matcher.end();
            lastWasEncodedWord = true;
        }

        result.append(header.substring(lastEnd));
        return result.toString();
    }

    /**
     * Checks whether the given text needs encoding for use in a header.
     *
     * @param text the text to check
     * @return true if the text contains non-ASCII characters
     */
    public static boolean needsEncoding(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 127) {
                return true;
            }
        }
        return false;
    }

    private static byte[] decodeQ(String encoded) {
        var out = new ByteArrayOutputStream(encoded.length());
        for (int i = 0; i < encoded.length(); i++) {
            char c = encoded.charAt(i);
            if (c == '_') {
                out.write(' ');
            } else if (c == '=' && i + 2 < encoded.length()) {
                int hi = Character.digit(encoded.charAt(i + 1), 16);
                int lo = Character.digit(encoded.charAt(i + 2), 16);
                if (hi >= 0 && lo >= 0) {
                    out.write((hi << 4) | lo);
                    i += 2;
                } else {
                    out.write(c);
                }
            } else {
                out.write(c);
            }
        }
        return out.toByteArray();
    }
}
