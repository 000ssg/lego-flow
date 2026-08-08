package ssg.legoflow.messaging.stomp.core;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * STOMP 1.2 frame parser and serializer.
 *
 * <p>Handles the text-based frame format including:
 * <ul>
 *   <li>Header value escaping: {@code \n} (newline), {@code \\} (backslash),
 *       {@code \c} (colon), {@code \r} (carriage return)</li>
 *   <li>Binary body support via {@code content-length} header</li>
 *   <li>Heart-beat detection (empty EOL frame)</li>
 *   <li>Multiple NULL bytes between frames (ignored per spec)</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class StompCodec {

    private static final byte NULL = 0;
    private static final byte LF = '\n';
    private static final byte CR = '\r';
    private static final byte COLON = ':';

    private StompCodec() {
    }

    // --- Serialization ---

    /**
     * Serializes a STOMP frame to its wire format bytes.
     *
     * @param frame the frame to serialize
     * @return the wire format bytes
     */
    public static byte[] encode(StompFrame frame) {
        if (frame.isHeartbeat()) {
            return new byte[]{LF};
        }

        var out = new ByteArrayOutputStream(256);

        // Command line
        var commandBytes = frame.command().name().getBytes(StandardCharsets.UTF_8);
        out.writeBytes(commandBytes);
        out.write(LF);

        // Headers
        for (var entry : frame.headers()) {
            var key = escapeHeaderValue(entry.getKey());
            var value = escapeHeaderValue(entry.getValue());
            out.writeBytes(key.getBytes(StandardCharsets.UTF_8));
            out.write(COLON);
            out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
            out.write(LF);
        }

        // Blank line separating headers from body
        out.write(LF);

        // Body
        if (frame.hasBody()) {
            out.writeBytes(frame.body());
        }

        // NULL terminator
        out.write(NULL);

        return out.toByteArray();
    }

    /**
     * Serializes a STOMP frame to a UTF-8 string (for text transports like WebSocket).
     *
     * @param frame the frame to serialize
     * @return the wire format string
     */
    public static String encodeToString(StompFrame frame) {
        return new String(encode(frame), StandardCharsets.UTF_8);
    }

    // --- Deserialization ---

    /**
     * Parses a STOMP frame from wire format bytes.
     *
     * <p>Leading EOLs are consumed (heart-beats or inter-frame whitespace).
     * If the input consists only of EOLs, a heart-beat frame is returned.
     *
     * @param data the wire format bytes
     * @return the parsed frame
     * @throws StompProtocolException if the frame is malformed
     */
    public static StompFrame decode(byte[] data) {
        if (data == null || data.length == 0) {
            throw new StompProtocolException("Empty frame data");
        }

        int pos = 0;

        // Skip leading EOLs (inter-frame whitespace or heart-beats)
        while (pos < data.length && (data[pos] == LF || data[pos] == CR)) {
            pos++;
        }

        // If only EOLs, it's a heart-beat
        if (pos >= data.length) {
            return StompFrame.heartbeat();
        }

        // Also handle case where remainder is just a NULL
        if (data[pos] == NULL) {
            return StompFrame.heartbeat();
        }

        // Parse command line
        int commandEnd = indexOf(data, LF, pos);
        if (commandEnd < 0) {
            throw new StompProtocolException("No newline after command");
        }

        // Handle optional CR before LF
        int commandLineEnd = commandEnd;
        if (commandLineEnd > pos && data[commandLineEnd - 1] == CR) {
            commandLineEnd--;
        }

        String commandStr = new String(data, pos, commandLineEnd - pos, StandardCharsets.UTF_8).trim();
        StompCommand command;
        try {
            command = StompCommand.fromString(commandStr);
        } catch (IllegalArgumentException e) {
            throw new StompProtocolException("Unknown command: " + commandStr, e);
        }

        pos = commandEnd + 1;

        // Parse headers
        var headers = new StompHeaders();
        while (pos < data.length) {
            // Check for end of headers (blank line)
            if (data[pos] == LF || (data[pos] == CR && pos + 1 < data.length && data[pos + 1] == LF)) {
                if (data[pos] == CR) pos++;
                pos++; // skip the LF
                break;
            }

            int lineEnd = indexOf(data, LF, pos);
            if (lineEnd < 0) {
                throw new StompProtocolException("Unterminated header line");
            }

            int lineContentEnd = lineEnd;
            if (lineContentEnd > pos && data[lineContentEnd - 1] == CR) {
                lineContentEnd--;
            }

            String line = new String(data, pos, lineContentEnd - pos, StandardCharsets.UTF_8);

            // Find the first unescaped colon
            int colonIdx = findUnescapedColon(line);
            if (colonIdx < 0) {
                throw new StompProtocolException("Header line missing colon: " + line);
            }

            String key = unescapeHeaderValue(line.substring(0, colonIdx));
            String value = unescapeHeaderValue(line.substring(colonIdx + 1));

            // First occurrence wins (STOMP 1.2 spec)
            headers.putIfAbsent(key, value);

            pos = lineEnd + 1;
        }

        // Parse body
        byte[] body;
        String contentLengthStr = headers.get(StompHeaders.CONTENT_LENGTH);
        if (contentLengthStr != null) {
            int contentLength;
            try {
                contentLength = Integer.parseInt(contentLengthStr.trim());
            } catch (NumberFormatException e) {
                throw new StompProtocolException("Invalid content-length: " + contentLengthStr, e);
            }
            if (pos + contentLength > data.length) {
                throw new StompProtocolException(
                        "Body shorter than content-length: expected " + contentLength
                                + " but only " + (data.length - pos) + " bytes available");
            }
            body = Arrays.copyOfRange(data, pos, pos + contentLength);
        } else {
            // Read until NULL terminator
            int nullIdx = indexOf(data, NULL, pos);
            if (nullIdx < 0) {
                // No NULL found — use rest of data as body
                body = Arrays.copyOfRange(data, pos, data.length);
            } else {
                body = Arrays.copyOfRange(data, pos, nullIdx);
            }
        }

        return new StompFrame(command, headers, body);
    }

    /**
     * Parses a STOMP frame from a UTF-8 string (for text transports like WebSocket).
     *
     * @param text the wire format string
     * @return the parsed frame
     * @throws StompProtocolException if the frame is malformed
     */
    public static StompFrame decodeFromString(String text) {
        return decode(text.getBytes(StandardCharsets.UTF_8));
    }

    // --- Header escaping (STOMP 1.2) ---

    /**
     * Escapes a header value per STOMP 1.2 spec.
     * <ul>
     *   <li>{@code \} → {@code \\}</li>
     *   <li>{@code \n} (newline) → {@code \n}</li>
     *   <li>{@code :} → {@code \c}</li>
     *   <li>{@code \r} (carriage return) → {@code \r}</li>
     * </ul>
     *
     * @param value the raw value
     * @return the escaped value
     */
    static String escapeHeaderValue(String value) {
        if (value == null || value.isEmpty()) return value;

        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case ':' -> sb.append("\\c");
                case '\r' -> sb.append("\\r");
                default -> sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Unescapes a header value per STOMP 1.2 spec.
     *
     * @param value the escaped value
     * @return the raw value
     */
    static String unescapeHeaderValue(String value) {
        if (value == null || value.isEmpty() || !value.contains("\\")) return value;

        var sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '\\' && i + 1 < value.length()) {
                char next = value.charAt(i + 1);
                switch (next) {
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'c' -> { sb.append(':'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    default -> sb.append(ch);
                }
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    // --- Helpers ---

    private static int indexOf(byte[] data, byte target, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == target) return i;
        }
        return -1;
    }

    /**
     * Finds the first colon that is not preceded by a backslash escape.
     */
    private static int findUnescapedColon(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ':') {
                // Count preceding backslashes
                int backslashes = 0;
                int j = i - 1;
                while (j >= 0 && line.charAt(j) == '\\') {
                    backslashes++;
                    j--;
                }
                // Colon is unescaped if preceded by even number of backslashes
                if (backslashes % 2 == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
