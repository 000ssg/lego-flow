package ssg.legoflow.ftp.protocol;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Codec for encoding and decoding FTP control channel messages.
 *
 * <p>FTP control messages are text-based, terminated by CRLF ({@code \r\n}).
 *
 * <p><strong>Command format:</strong> {@code COMMAND SP argument CRLF}
 * <p><strong>Reply format:</strong>
 * <ul>
 *   <li>Single-line: {@code code SP text CRLF}</li>
 *   <li>Multi-line: {@code code-text CRLF ... code SP text CRLF}</li>
 * </ul>
 *
 * @since 1.0.0
 */
public final class FtpProtocolCodec {

    private static final String CRLF = "\r\n";

    private FtpProtocolCodec() {
        // utility class
    }

    // ---- Command encoding/decoding ----

    /**
     * Encodes an FTP command with an optional argument.
     *
     * @param command  the FTP command
     * @param argument the argument (may be {@code null})
     * @return the encoded command string (with CRLF)
     */
    public static String encodeCommand(FtpCommand command, String argument) {
        Objects.requireNonNull(command, "command");
        if (argument == null || argument.isEmpty()) {
            return command.wireForm() + CRLF;
        }
        return command.wireForm() + " " + argument + CRLF;
    }

    /**
     * Encodes an FTP command without an argument.
     *
     * @param command the FTP command
     * @return the encoded command string (with CRLF)
     */
    public static String encodeCommand(FtpCommand command) {
        return encodeCommand(command, null);
    }

    /**
     * Decodes an FTP command line (without trailing CRLF) into command and argument.
     *
     * @param line the command line
     * @return a two-element array: [command, argument] where argument may be {@code null}
     * @throws IllegalArgumentException if the line is empty or contains an unrecognized command
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

    // ---- Reply encoding/decoding ----

    /**
     * Encodes an FTP reply into its wire format.
     *
     * @param reply the reply to encode
     * @return the encoded reply string (with CRLF line terminators)
     */
    public static String encodeReply(FtpReply reply) {
        Objects.requireNonNull(reply, "reply");
        var sb = new StringBuilder();
        List<String> lines = reply.lines();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(reply.code());
            if (i < lines.size() - 1) {
                sb.append('-');
            } else {
                sb.append(' ');
            }
            sb.append(lines.get(i));
            sb.append(CRLF);
        }
        return sb.toString();
    }

    /**
     * Reads a complete FTP reply from a buffered reader, handling multi-line replies.
     *
     * @param reader the reader connected to the control channel
     * @return the decoded reply, or {@code null} if the stream is closed
     * @throws IOException              if an I/O error occurs
     * @throws IllegalArgumentException if the reply format is invalid
     */
    public static FtpReply readReply(BufferedReader reader) throws IOException {
        Objects.requireNonNull(reader, "reader");
        String firstLine = reader.readLine();
        if (firstLine == null) {
            return null;
        }
        if (firstLine.length() < 4) {
            throw new IllegalArgumentException("Invalid FTP reply (too short): " + firstLine);
        }

        int code = parseReplyCode(firstLine);
        char separator = firstLine.charAt(3);
        String text = firstLine.substring(4);

        if (separator == ' ') {
            // Single-line reply
            return new FtpReply(code, text);
        } else if (separator == '-') {
            // Multi-line reply
            List<String> lines = new ArrayList<>();
            lines.add(text);
            String endPrefix = String.valueOf(code) + ' ';
            while (true) {
                String nextLine = reader.readLine();
                if (nextLine == null) {
                    break;
                }
                if (nextLine.startsWith(endPrefix)) {
                    lines.add(nextLine.substring(4));
                    break;
                }
                // Intermediate lines may or may not start with code-
                String dashPrefix = String.valueOf(code) + '-';
                if (nextLine.startsWith(dashPrefix)) {
                    lines.add(nextLine.substring(4));
                } else {
                    lines.add(nextLine);
                }
            }
            return new FtpReply(code, lines);
        } else {
            throw new IllegalArgumentException("Invalid reply separator at position 3: '" + separator + "'");
        }
    }

    /**
     * Decodes a complete reply from a string (may contain multiple CRLF-separated lines).
     *
     * @param raw the raw reply text
     * @return the decoded reply
     * @throws IOException if parsing fails
     */
    public static FtpReply decodeReply(String raw) throws IOException {
        Objects.requireNonNull(raw, "raw");
        try (var reader = new BufferedReader(new StringReader(raw))) {
            return readReply(reader);
        }
    }

    /**
     * Writes a command to an output stream.
     *
     * @param out      the output stream
     * @param command  the command
     * @param argument the argument (may be {@code null})
     * @throws IOException if an I/O error occurs
     */
    public static void writeCommand(OutputStream out, FtpCommand command, String argument) throws IOException {
        out.write(encodeCommand(command, argument).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Writes a reply to an output stream.
     *
     * @param out   the output stream
     * @param reply the reply to write
     * @throws IOException if an I/O error occurs
     */
    public static void writeReply(OutputStream out, FtpReply reply) throws IOException {
        out.write(encodeReply(reply).getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static int parseReplyCode(String line) {
        try {
            return Integer.parseInt(line.substring(0, 3));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid reply code in: " + line, e);
        }
    }
}
