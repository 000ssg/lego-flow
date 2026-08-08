package ssg.legoflow.ftp.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an FTP reply message from the server.
 *
 * <p>An FTP reply consists of a three-digit code and one or more lines of text.
 * Single-line replies have the format: {@code code SP text CRLF}.
 * Multi-line replies have the format:
 * <pre>
 *   code-first line CRLF
 *   additional lines CRLF
 *   code SP last line CRLF
 * </pre>
 *
 * @since 0.1.0
 */
public final class FtpReply {

    private final int code;
    private final List<String> lines;

    /**
     * Creates a single-line reply.
     *
     * @param code the three-digit reply code
     * @param text the reply text
     */
    public FtpReply(int code, String text) {
        this(code, List.of(Objects.requireNonNull(text, "text")));
    }

    /**
     * Creates a reply with the given code and lines.
     *
     * @param code  the three-digit reply code
     * @param lines the reply text lines (must not be empty)
     */
    public FtpReply(int code, List<String> lines) {
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException("Reply code must be 100-599, got: " + code);
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Reply must have at least one line");
        }
        this.code = code;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * Creates a reply from a known reply code with its default description.
     *
     * @param replyCode the reply code enum
     * @return the reply
     */
    public static FtpReply of(FtpReplyCode replyCode) {
        return new FtpReply(replyCode.code(), replyCode.description());
    }

    /**
     * Creates a reply with a custom message.
     *
     * @param replyCode the reply code enum
     * @param text      custom reply text
     * @return the reply
     */
    public static FtpReply of(FtpReplyCode replyCode, String text) {
        return new FtpReply(replyCode.code(), text);
    }

    /**
     * Returns the three-digit reply code.
     *
     * @return the reply code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the first line of text (most common usage).
     *
     * @return the reply text
     */
    public String text() {
        return lines.getFirst();
    }

    /**
     * Returns all reply lines.
     *
     * @return unmodifiable list of reply lines
     */
    public List<String> lines() {
        return lines;
    }

    /**
     * Returns {@code true} if this is a multi-line reply.
     *
     * @return true if more than one line
     */
    public boolean isMultiLine() {
        return lines.size() > 1;
    }

    /**
     * Returns {@code true} if this is a positive completion (2xx).
     *
     * @return true for success replies
     */
    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    /**
     * Returns {@code true} if this is a positive intermediate (3xx).
     *
     * @return true for intermediate replies
     */
    public boolean isIntermediate() {
        return code >= 300 && code < 400;
    }

    /**
     * Returns {@code true} if this is a negative reply (4xx or 5xx).
     *
     * @return true for error replies
     */
    public boolean isNegative() {
        return code >= 400;
    }

    /**
     * Attempts to resolve the reply code to a known {@link FtpReplyCode}.
     *
     * @return the matching reply code, or {@code null} if not a standard code
     */
    public FtpReplyCode replyCode() {
        try {
            return FtpReplyCode.fromCode(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        if (lines.size() == 1) {
            return code + " " + lines.getFirst();
        }
        var sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(code);
            sb.append(i < lines.size() - 1 ? '-' : ' ');
            sb.append(lines.get(i));
            if (i < lines.size() - 1) {
                sb.append("\r\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof FtpReply other)) return false;
        return code == other.code && lines.equals(other.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, lines);
    }
}
