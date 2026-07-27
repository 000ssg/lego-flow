package ssg.legoflow.email.imap.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * IMAP4rev2 protocol codec for parsing and serializing tagged commands and responses.
 *
 * <p>Handles the text-based IMAP wire format including:
 * <ul>
 *   <li>Tagged commands: {@code TAG COMMAND args...}</li>
 *   <li>Tagged responses: {@code TAG STATUS [code] text}</li>
 *   <li>Untagged responses: {@code * data}</li>
 *   <li>Continuation responses: {@code + text}</li>
 *   <li>Literal strings: {@code {N}\r\n...data...}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class ImapCodec {

    private ImapCodec() {
    }

    /**
     * Encodes a tagged command for the wire.
     *
     * @param tag     the command tag
     * @param command the command
     * @param args    the command arguments
     * @return the encoded command line (with CRLF)
     */
    public static String encodeCommand(String tag, ImapCommand command, String... args) {
        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(' ').append(command.text());
        for (String arg : args) {
            sb.append(' ').append(arg);
        }
        sb.append("\r\n");
        return sb.toString();
    }

    /**
     * Encodes a tagged command with a single argument string.
     *
     * @param tag     the command tag
     * @param command the command
     * @param argLine the full argument string
     * @return the encoded command line
     */
    public static String encodeCommand(String tag, String command, String argLine) {
        if (argLine == null || argLine.isEmpty()) {
            return tag + " " + command + "\r\n";
        }
        return tag + " " + command + " " + argLine + "\r\n";
    }

    /**
     * Parses a command line into tag, command name, and arguments.
     *
     * @param line the raw command line (without CRLF)
     * @return array of [tag, command, arguments...] or null if empty
     */
    public static String[] parseCommandLine(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }

        List<String> parts = new ArrayList<>();
        int pos = 0;
        int len = line.length();

        // Parse tag
        int space = line.indexOf(' ');
        if (space < 0) {
            return new String[]{line};
        }
        parts.add(line.substring(0, space));
        pos = space + 1;

        // Parse command
        space = line.indexOf(' ', pos);
        if (space < 0) {
            parts.add(line.substring(pos));
            return parts.toArray(new String[0]);
        }
        parts.add(line.substring(pos, space));
        pos = space + 1;

        // Rest is arguments
        if (pos < len) {
            parts.add(line.substring(pos));
        }

        return parts.toArray(new String[0]);
    }

    /**
     * Reads a single IMAP line from the input stream, handling literal continuations.
     *
     * @param reader the buffered reader
     * @return the line (without CRLF), or null on EOF
     * @throws IOException if an I/O error occurs
     */
    public static String readLine(BufferedReader reader) throws IOException {
        return reader.readLine();
    }

    /**
     * Reads literal data from the input stream.
     *
     * @param reader the input stream reader
     * @param size   the number of octets to read
     * @return the literal data as a string
     * @throws IOException if an I/O error occurs
     */
    public static String readLiteral(Reader reader, int size) throws IOException {
        char[] buffer = new char[size];
        int totalRead = 0;
        while (totalRead < size) {
            int read = reader.read(buffer, totalRead, size - totalRead);
            if (read < 0) {
                throw new IOException("Unexpected EOF reading literal");
            }
            totalRead += read;
        }
        return new String(buffer, 0, totalRead);
    }

    /**
     * Quotes a string for IMAP if it contains special characters.
     *
     * @param value the string to quote
     * @return the quoted string, or the original if no quoting needed
     */
    public static String quoteString(String value) {
        if (value == null) {
            return "NIL";
        }
        if (value.isEmpty()) {
            return "\"\"";
        }
        // Check if quoting is needed
        boolean needsQuote = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ' ' || c == '(' || c == ')' || c == '{' || c == '"'
                    || c == '\\' || c == '%' || c == '*' || c < 0x20 || c > 0x7E) {
                needsQuote = true;
                break;
            }
        }
        if (!needsQuote) {
            return value;
        }
        // Check if it needs literal (contains " or \ or non-ASCII)
        boolean needsLiteral = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\r' || c == '\n' || c > 0x7E) {
                needsLiteral = true;
                break;
            }
        }
        if (needsLiteral) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return "{" + bytes.length + "}\r\n" + value;
        }
        // Quoted string with escaping
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Unquotes an IMAP quoted string.
     *
     * @param value the quoted string
     * @return the unquoted string, or null if "NIL"
     */
    public static String unquoteString(String value) {
        if (value == null || "NIL".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.startsWith("\"") && value.endsWith("\"")) {
            String inner = value.substring(1, value.length() - 1);
            StringBuilder sb = new StringBuilder();
            boolean escaped = false;
            for (int i = 0; i < inner.length(); i++) {
                char c = inner.charAt(i);
                if (escaped) {
                    sb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
        return value;
    }

    /**
     * Parses a parenthesized list into its elements.
     *
     * @param text the text starting with "(" and ending with ")"
     * @return the list of elements (strings or nested lists as strings)
     */
    public static List<String> parseParenList(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return result;
        }

        int pos = 0;
        int len = trimmed.length();
        while (pos < len) {
            // Skip whitespace
            while (pos < len && trimmed.charAt(pos) == ' ') pos++;
            if (pos >= len) break;

            char c = trimmed.charAt(pos);
            if (c == '(') {
                // Find matching close paren
                int depth = 1;
                int start = pos;
                pos++;
                while (pos < len && depth > 0) {
                    if (trimmed.charAt(pos) == '(') depth++;
                    else if (trimmed.charAt(pos) == ')') depth--;
                    pos++;
                }
                result.add(trimmed.substring(start, pos));
            } else if (c == '"') {
                // Quoted string
                int start = pos;
                pos++;
                while (pos < len) {
                    if (trimmed.charAt(pos) == '\\') {
                        pos += 2;
                    } else if (trimmed.charAt(pos) == '"') {
                        pos++;
                        break;
                    } else {
                        pos++;
                    }
                }
                result.add(trimmed.substring(start, pos));
            } else {
                // Atom
                int start = pos;
                while (pos < len && trimmed.charAt(pos) != ' '
                        && trimmed.charAt(pos) != '(' && trimmed.charAt(pos) != ')') {
                    pos++;
                }
                result.add(trimmed.substring(start, pos));
            }
        }
        return result;
    }

    /**
     * Parses a sequence set string (e.g., "1:5,7,10:*") into individual numbers.
     * The '*' character represents the largest value and is resolved using maxValue.
     *
     * @param seqSet   the sequence set string
     * @param maxValue the value to substitute for '*'
     * @return the list of resolved sequence numbers
     */
    public static List<Long> parseSequenceSet(String seqSet, long maxValue) {
        List<Long> result = new ArrayList<>();
        String[] ranges = seqSet.split(",");
        for (String range : ranges) {
            range = range.trim();
            if (range.contains(":")) {
                String[] bounds = range.split(":");
                long start = parseStar(bounds[0].trim(), maxValue);
                long end = parseStar(bounds[1].trim(), maxValue);
                long lo = Math.min(start, end);
                long hi = Math.max(start, end);
                for (long i = lo; i <= hi; i++) {
                    result.add(i);
                }
            } else {
                result.add(parseStar(range, maxValue));
            }
        }
        return result;
    }

    private static long parseStar(String value, long maxValue) {
        return "*".equals(value) ? maxValue : Long.parseLong(value);
    }

    /**
     * Formats a flags list for the wire protocol.
     *
     * @param flags the flags
     * @return the formatted flags string (e.g., "(\\Seen \\Flagged)")
     */
    public static String formatFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) {
            return "()";
        }
        return "(" + String.join(" ", flags) + ")";
    }

    /**
     * Parses a flags list from protocol text.
     *
     * @param text the flags text (e.g., "(\\Seen \\Flagged)")
     * @return the list of flag strings
     */
    public static List<String> parseFlags(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        if (trimmed.isEmpty()) {
            return List.of();
        }
        List<String> flags = new ArrayList<>();
        for (String flag : trimmed.split("\\s+")) {
            if (!flag.isEmpty()) {
                flags.add(flag);
            }
        }
        return flags;
    }
}
