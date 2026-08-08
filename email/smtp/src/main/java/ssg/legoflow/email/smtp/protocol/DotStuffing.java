package ssg.legoflow.email.smtp.protocol;

/**
 * Dot-stuffing codec for the SMTP DATA command per RFC 5321 section 4.5.2.
 *
 * <p>In the DATA command, the message body is terminated by a line containing only
 * a single period ({@code .}). To allow periods at the start of lines in message
 * content, any line starting with a period is prefixed with an additional period
 * (dot-stuffing). The receiver removes the extra leading period.
 *
 * <p>Rules:
 * <ul>
 *   <li>Sending: if a line starts with '.', prepend another '.'</li>
 *   <li>Receiving: if a line starts with '.', remove the first '.'</li>
 *   <li>End-of-data: a line containing only '.' (after unstuffing) ends the message</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class DotStuffing {

    /** The end-of-data marker: a line containing only a single period. */
    public static final String END_OF_DATA = ".";

    private DotStuffing() {
        // utility class
    }

    /**
     * Applies dot-stuffing to a message body for transmission via DATA.
     *
     * <p>Each line that starts with a period gets an extra period prepended.
     * Lines are separated by CRLF. The result does NOT include the final
     * {@code CRLF.CRLF} end-of-data marker.
     *
     * @param body the raw message body
     * @return the dot-stuffed body ready for DATA transmission
     */
    public static String stuff(String body) {
        if (body == null || body.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder(body.length() + 64);
        String[] lines = body.split("\r\n|\r|\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(".")) {
                sb.append('.');
            }
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\r\n");
            }
        }
        return sb.toString();
    }

    /**
     * Removes dot-stuffing from a received message body.
     *
     * <p>Each line that starts with two periods has the leading period removed.
     * Lines consisting of only a single period are treated as end-of-data markers
     * and are not included in the result.
     *
     * @param stuffedBody the dot-stuffed body as received
     * @return the original message body
     */
    public static String unstuff(String stuffedBody) {
        if (stuffedBody == null || stuffedBody.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder(stuffedBody.length());
        String[] lines = stuffedBody.split("\r\n|\r|\n", -1);
        boolean first = true;
        for (String line : lines) {
            if (line.equals(".")) {
                // end of data marker, stop
                break;
            }
            if (!first) {
                sb.append("\r\n");
            }
            if (line.startsWith("..")) {
                sb.append(line.substring(1));
            } else {
                sb.append(line);
            }
            first = false;
        }
        return sb.toString();
    }

    /**
     * Stuffs a single line (without CRLF).
     *
     * @param line the line to stuff
     * @return the stuffed line
     */
    public static String stuffLine(String line) {
        if (line != null && line.startsWith(".")) {
            return "." + line;
        }
        return line;
    }

    /**
     * Unstuffs a single line (without CRLF).
     *
     * @param line the line to unstuff
     * @return the unstuffed line, or {@code null} if this is the end-of-data marker
     */
    public static String unstuffLine(String line) {
        if (line == null) {
            return null;
        }
        if (line.equals(".")) {
            return null; // end-of-data marker
        }
        if (line.startsWith("..")) {
            return line.substring(1);
        }
        return line;
    }

    /**
     * Returns {@code true} if the line is the end-of-data marker.
     *
     * @param line the line to check
     * @return true if the line is exactly "."
     */
    public static boolean isEndOfData(String line) {
        return ".".equals(line);
    }
}
