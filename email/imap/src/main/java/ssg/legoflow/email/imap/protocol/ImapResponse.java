package ssg.legoflow.email.imap.protocol;

import java.util.Objects;

/**
 * Represents an IMAP response line, either tagged or untagged.
 *
 * <p>Tagged responses have a tag matching the command that triggered them
 * and a status (OK, NO, BAD). Untagged responses use {@code *} as the tag
 * and carry data such as mailbox information, message listings, or
 * capability announcements.
 *
 * @since 0.1.0
 */
public final class ImapResponse {

    private final String tag;
    private final ImapStatus status;
    private final String responseCode;
    private final String text;
    private final String rawLine;

    private ImapResponse(String tag, ImapStatus status, String responseCode, String text, String rawLine) {
        this.tag = tag;
        this.status = status;
        this.responseCode = responseCode;
        this.text = text;
        this.rawLine = rawLine;
    }

    /**
     * Creates a tagged response.
     *
     * @param tag    the command tag
     * @param status the response status
     * @param text   the human-readable text
     * @return the tagged response
     */
    public static ImapResponse tagged(String tag, ImapStatus status, String text) {
        return new ImapResponse(tag, status, null, text,
                tag + " " + status.text() + " " + text);
    }

    /**
     * Creates a tagged response with a response code.
     *
     * @param tag          the command tag
     * @param status       the response status
     * @param responseCode the bracketed response code (e.g., "READ-WRITE")
     * @param text         the human-readable text
     * @return the tagged response
     */
    public static ImapResponse tagged(String tag, ImapStatus status, String responseCode, String text) {
        return new ImapResponse(tag, status, responseCode, text,
                tag + " " + status.text() + " [" + responseCode + "] " + text);
    }

    /**
     * Creates an untagged response.
     *
     * @param text the response text (e.g., "5 EXISTS" or "FLAGS (\\Seen \\Answered)")
     * @return the untagged response
     */
    public static ImapResponse untagged(String text) {
        return new ImapResponse(ImapTag.UNTAGGED, null, null, text,
                "* " + text);
    }

    /**
     * Creates an untagged response with a status.
     *
     * @param status the status
     * @param text   the text
     * @return the untagged response
     */
    public static ImapResponse untagged(ImapStatus status, String text) {
        return new ImapResponse(ImapTag.UNTAGGED, status, null, text,
                "* " + status.text() + " " + text);
    }

    /**
     * Creates an untagged response with status and response code.
     *
     * @param status       the status
     * @param responseCode the response code
     * @param text         the text
     * @return the untagged response
     */
    public static ImapResponse untagged(ImapStatus status, String responseCode, String text) {
        return new ImapResponse(ImapTag.UNTAGGED, status, responseCode, text,
                "* " + status.text() + " [" + responseCode + "] " + text);
    }

    /**
     * Creates a continuation response.
     *
     * @param text the continuation text
     * @return the continuation response
     */
    public static ImapResponse continuation(String text) {
        return new ImapResponse(ImapTag.CONTINUATION, null, null, text,
                "+ " + text);
    }

    /**
     * Parses a raw IMAP response line.
     *
     * @param line the raw line (without trailing CRLF)
     * @return the parsed response
     */
    public static ImapResponse parse(String line) {
        Objects.requireNonNull(line);
        if (line.startsWith("+ ")) {
            return continuation(line.substring(2));
        }
        if (line.startsWith("+\r") || line.equals("+")) {
            return continuation("");
        }

        int spaceIdx = line.indexOf(' ');
        if (spaceIdx < 0) {
            return new ImapResponse(line, null, null, "", line);
        }

        String tag = line.substring(0, spaceIdx);
        String rest = line.substring(spaceIdx + 1);

        // Try to parse status
        int nextSpace = rest.indexOf(' ');
        String statusText = nextSpace >= 0 ? rest.substring(0, nextSpace) : rest;
        String remaining = nextSpace >= 0 ? rest.substring(nextSpace + 1) : "";

        ImapStatus status = null;
        try {
            status = ImapStatus.parse(statusText);
        } catch (IllegalArgumentException e) {
            // Not a status response - treat the whole rest as text
            return new ImapResponse(tag, null, null, rest, line);
        }

        // Check for response code [...]
        String responseCode = null;
        if (remaining.startsWith("[")) {
            int closeBracket = remaining.indexOf(']');
            if (closeBracket > 0) {
                responseCode = remaining.substring(1, closeBracket);
                remaining = closeBracket + 2 < remaining.length()
                        ? remaining.substring(closeBracket + 2) : "";
            }
        }

        return new ImapResponse(tag, status, responseCode, remaining, line);
    }

    /** Returns the tag, {@code *} for untagged, or {@code +} for continuation. */
    public String tag() { return tag; }

    /** Returns the status if present, or null. */
    public ImapStatus status() { return status; }

    /** Returns the response code if present (e.g., "UIDNEXT 100"), or null. */
    public String responseCode() { return responseCode; }

    /** Returns the human-readable text portion. */
    public String text() { return text; }

    /** Returns the full raw response line. */
    public String rawLine() { return rawLine; }

    /** Returns true if this is an untagged response. */
    public boolean isUntagged() { return ImapTag.UNTAGGED.equals(tag); }

    /** Returns true if this is a continuation response. */
    public boolean isContinuation() { return ImapTag.CONTINUATION.equals(tag); }

    /** Returns true if this is a tagged response. */
    public boolean isTagged() { return !isUntagged() && !isContinuation(); }

    /** Returns true if the status is OK. */
    public boolean isOk() { return status == ImapStatus.OK; }

    /** Returns true if the status is NO. */
    public boolean isNo() { return status == ImapStatus.NO; }

    /** Returns true if the status is BAD. */
    public boolean isBad() { return status == ImapStatus.BAD; }

    /**
     * Formats this response for the wire (with CRLF).
     *
     * @return the formatted response line
     */
    public String toWire() {
        return rawLine + "\r\n";
    }

    @Override
    public String toString() {
        return rawLine;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImapResponse that)) return false;
        return Objects.equals(rawLine, that.rawLine);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawLine);
    }
}
