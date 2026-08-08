package ssg.legoflow.email.smtp.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Codec for encoding and decoding SMTP protocol messages.
 *
 * <p>SMTP messages are text-based, terminated by CRLF ({@code \r\n}).
 *
 * <p><strong>Command format:</strong> {@code VERB SP parameters CRLF}
 * <p><strong>Reply format:</strong>
 * <ul>
 *   <li>Single-line: {@code code SP text CRLF}</li>
 *   <li>Multi-line: {@code code-text CRLF ... code SP text CRLF}</li>
 *   <li>Enhanced: {@code code SP X.Y.Z text CRLF}</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class SmtpCodec {

    private static final String CRLF = "\r\n";
    private static final Pattern ENHANCED_PATTERN = Pattern.compile("^([245])\\.([0-9]+)\\.([0-9]+)\\s");

    private SmtpCodec() {
        // utility class
    }

    // ---- Command encoding ----

    /**
     * Encodes an SMTP command with parameters.
     *
     * @param command    the SMTP command
     * @param parameters the parameters (may be {@code null})
     * @return the encoded command string (with CRLF)
     */
    public static String encodeCommand(SmtpCommand command, String parameters) {
        Objects.requireNonNull(command, "command");
        if (parameters == null || parameters.isEmpty()) {
            return command.wireForm() + CRLF;
        }
        return command.wireForm() + " " + parameters + CRLF;
    }

    /**
     * Encodes an SMTP command without parameters.
     *
     * @param command the SMTP command
     * @return the encoded command string (with CRLF)
     */
    public static String encodeCommand(SmtpCommand command) {
        return encodeCommand(command, null);
    }

    /**
     * Encodes a MAIL FROM command with optional extension parameters.
     *
     * @param sender the sender address (without angle brackets)
     * @param params optional extension parameters (e.g., "SIZE=1024 BODY=8BITMIME")
     * @return the encoded command string
     */
    public static String encodeMailFrom(String sender, String params) {
        var sb = new StringBuilder("MAIL FROM:<").append(sender != null ? sender : "").append('>');
        if (params != null && !params.isEmpty()) {
            sb.append(' ').append(params);
        }
        sb.append(CRLF);
        return sb.toString();
    }

    /**
     * Encodes a RCPT TO command with optional extension parameters.
     *
     * @param recipient the recipient address (without angle brackets)
     * @param params    optional extension parameters (e.g., "NOTIFY=SUCCESS,FAILURE")
     * @return the encoded command string
     */
    public static String encodeRcptTo(String recipient, String params) {
        var sb = new StringBuilder("RCPT TO:<").append(recipient).append('>');
        if (params != null && !params.isEmpty()) {
            sb.append(' ').append(params);
        }
        sb.append(CRLF);
        return sb.toString();
    }

    /**
     * Encodes a BDAT command.
     *
     * @param chunkSize the chunk size in bytes
     * @param last      true if this is the last chunk
     * @return the encoded command string
     */
    public static String encodeBdat(int chunkSize, boolean last) {
        return "BDAT " + chunkSize + (last ? " LAST" : "") + CRLF;
    }

    // ---- Command decoding ----

    /**
     * Decodes an SMTP command line (without trailing CRLF) into command and parameters.
     *
     * @param line the command line
     * @return a two-element array: [command-name, parameters] where parameters may be {@code null}
     * @throws IllegalArgumentException if the line is empty
     */
    public static String[] decodeCommand(String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalArgumentException("Command line must not be null or blank");
        }
        String trimmed = line.stripTrailing();
        int spaceIdx = trimmed.indexOf(' ');
        if (spaceIdx < 0) {
            return new String[]{trimmed.toUpperCase(), null};
        }
        return new String[]{
                trimmed.substring(0, spaceIdx).toUpperCase(),
                trimmed.substring(spaceIdx + 1)
        };
    }

    /**
     * Parses a MAIL FROM parameter string to extract the sender address.
     *
     * @param params the MAIL FROM parameters (e.g., "FROM:&lt;user@example.com&gt; SIZE=1024")
     * @return the sender address without angle brackets
     */
    public static String parseMailFromAddress(String params) {
        return parseAngleBracketAddress(params, "FROM:");
    }

    /**
     * Parses a RCPT TO parameter string to extract the recipient address.
     *
     * @param params the RCPT TO parameters (e.g., "TO:&lt;user@example.com&gt; NOTIFY=SUCCESS")
     * @return the recipient address without angle brackets
     */
    public static String parseRcptToAddress(String params) {
        return parseAngleBracketAddress(params, "TO:");
    }

    /**
     * Parses MAIL FROM/RCPT TO extension parameters (everything after the address).
     *
     * @param params the full parameter string including the address
     * @return the extension parameters, or empty string if none
     */
    public static String parseExtensionParams(String params) {
        if (params == null) return "";
        int gtIdx = params.indexOf('>');
        if (gtIdx < 0) return "";
        String rest = params.substring(gtIdx + 1).trim();
        return rest;
    }

    /**
     * Parses BDAT parameters.
     *
     * @param params the BDAT parameter string (e.g., "1024 LAST")
     * @return two-element array: [chunk-size-string, "LAST" or null]
     */
    public static String[] parseBdatParams(String params) {
        if (params == null || params.isBlank()) {
            throw new IllegalArgumentException("BDAT requires chunk size parameter");
        }
        String[] parts = params.trim().split("\\s+", 2);
        String size = parts[0];
        String last = parts.length > 1 && parts[1].trim().equalsIgnoreCase("LAST") ? "LAST" : null;
        return new String[]{size, last};
    }

    // ---- Reply encoding ----

    /**
     * Encodes an SMTP reply to wire format.
     *
     * @param reply the reply to encode
     * @return the encoded reply string (with CRLF line endings)
     */
    public static String encodeReply(SmtpReply reply) {
        Objects.requireNonNull(reply, "reply");
        var sb = new StringBuilder();
        List<String> lines = reply.lines();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(reply.code());
            boolean isLast = (i == lines.size() - 1);
            sb.append(isLast ? ' ' : '-');
            if (reply.enhancedCode() != null) {
                sb.append(reply.enhancedCode().wireForm()).append(' ');
            }
            sb.append(lines.get(i));
            sb.append(CRLF);
        }
        return sb.toString();
    }

    // ---- Reply decoding ----

    /**
     * Reads and decodes an SMTP reply from a buffered reader.
     *
     * <p>Handles multi-line replies where intermediate lines use
     * {@code code-text} format and the final line uses {@code code SP text}.
     *
     * @param reader the input reader
     * @return the decoded reply
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if the reply format is invalid
     */
    public static SmtpReply readReply(BufferedReader reader) throws IOException {
        var lines = new ArrayList<String>();
        int code = -1;
        EnhancedStatusCode enhanced = null;

        while (true) {
            String rawLine = reader.readLine();
            if (rawLine == null) {
                if (lines.isEmpty()) {
                    throw new IOException("Connection closed before reply received");
                }
                break;
            }

            if (rawLine.length() < 3) {
                throw new IllegalArgumentException("Invalid reply line (too short): " + rawLine);
            }

            int lineCode;
            try {
                lineCode = Integer.parseInt(rawLine.substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid reply code: " + rawLine);
            }

            if (code == -1) {
                code = lineCode;
            } else if (lineCode != code) {
                throw new IllegalArgumentException("Inconsistent reply codes: expected " + code + ", got " + lineCode);
            }

            boolean isContinuation;
            String text;
            if (rawLine.length() == 3) {
                isContinuation = false;
                text = "";
            } else {
                char separator = rawLine.charAt(3);
                isContinuation = (separator == '-');
                text = rawLine.substring(4);
            }

            // Try to parse enhanced status code from first line
            if (lines.isEmpty()) {
                var matcher = ENHANCED_PATTERN.matcher(text);
                if (matcher.find()) {
                    try {
                        enhanced = EnhancedStatusCode.parse(text.substring(0, matcher.end() - 1));
                        text = text.substring(matcher.end()).trim();
                    } catch (IllegalArgumentException e) {
                        // Not a valid enhanced code, keep text as-is
                    }
                }
            } else if (enhanced != null) {
                // Strip enhanced code from continuation/final lines too
                var matcher = ENHANCED_PATTERN.matcher(text);
                if (matcher.find()) {
                    text = text.substring(matcher.end()).trim();
                }
            }

            lines.add(text);

            if (!isContinuation) {
                break;
            }
        }

        if (code == -1) {
            throw new IOException("No reply received");
        }

        return new SmtpReply(code, enhanced, lines);
    }

    /**
     * Reads a line from a buffered reader (CRLF terminated).
     *
     * @param reader the reader
     * @return the line without CRLF, or null if end of stream
     * @throws IOException if an I/O error occurs
     */
    public static String readLine(BufferedReader reader) throws IOException {
        return reader.readLine();
    }

    /**
     * Writes a command string to a writer and flushes.
     *
     * @param writer  the writer
     * @param command the encoded command string (should include CRLF)
     * @throws IOException if an I/O error occurs
     */
    public static void writeCommand(Writer writer, String command) throws IOException {
        writer.write(command);
        writer.flush();
    }

    /**
     * Writes a reply string to a writer and flushes.
     *
     * @param writer the writer
     * @param reply  the reply to encode and send
     * @throws IOException if an I/O error occurs
     */
    public static void writeReply(Writer writer, SmtpReply reply) throws IOException {
        writer.write(encodeReply(reply));
        writer.flush();
    }

    // ---- Private helpers ----

    private static String parseAngleBracketAddress(String params, String prefix) {
        if (params == null || params.isBlank()) {
            return "";
        }
        String upper = params.trim();
        // Find the prefix (case-insensitive)
        int prefixIdx = upper.toUpperCase().indexOf(prefix);
        String working;
        if (prefixIdx >= 0) {
            working = params.substring(prefixIdx + prefix.length()).trim();
        } else {
            working = params.trim();
        }
        // Extract address from angle brackets
        int ltIdx = working.indexOf('<');
        int gtIdx = working.indexOf('>');
        if (ltIdx >= 0 && gtIdx > ltIdx) {
            return working.substring(ltIdx + 1, gtIdx);
        }
        // No angle brackets: return as-is (non-standard but lenient)
        int spIdx = working.indexOf(' ');
        return spIdx >= 0 ? working.substring(0, spIdx) : working;
    }
}
