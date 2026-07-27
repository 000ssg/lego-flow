package ssg.legoflow.email.smtp.protocol;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents an SMTP reply from the server.
 *
 * <p>An SMTP reply consists of a three-digit code, an optional enhanced status code,
 * and one or more lines of text. Single-line replies have the format:
 * {@code code SP [enhanced SP] text CRLF}. Multi-line replies:
 * <pre>
 *   code-[enhanced SP]first line CRLF
 *   code-additional lines CRLF
 *   code SP [enhanced SP]last line CRLF
 * </pre>
 *
 * @since 1.0.0
 */
public final class SmtpReply {

    private final int code;
    private final EnhancedStatusCode enhancedCode;
    private final List<String> lines;

    /**
     * Creates a single-line reply.
     *
     * @param code the three-digit reply code
     * @param text the reply text
     */
    public SmtpReply(int code, String text) {
        this(code, null, List.of(Objects.requireNonNull(text, "text")));
    }

    /**
     * Creates a single-line reply with an enhanced status code.
     *
     * @param code         the three-digit reply code
     * @param enhancedCode the enhanced status code (may be {@code null})
     * @param text         the reply text
     */
    public SmtpReply(int code, EnhancedStatusCode enhancedCode, String text) {
        this(code, enhancedCode, List.of(Objects.requireNonNull(text, "text")));
    }

    /**
     * Creates a reply with the given code, enhanced code, and lines.
     *
     * @param code         the three-digit reply code
     * @param enhancedCode the enhanced status code (may be {@code null})
     * @param lines        the reply text lines (must not be empty)
     */
    public SmtpReply(int code, EnhancedStatusCode enhancedCode, List<String> lines) {
        if (code < 200 || code > 599) {
            throw new IllegalArgumentException("Reply code must be 200-599, got: " + code);
        }
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Reply must have at least one line");
        }
        this.code = code;
        this.enhancedCode = enhancedCode;
        this.lines = Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * Creates a simple reply without enhanced status code.
     *
     * @param code the reply code
     * @param text the reply text
     * @return the reply
     */
    public static SmtpReply of(int code, String text) {
        return new SmtpReply(code, text);
    }

    /**
     * Creates a reply with an enhanced status code.
     *
     * @param code         the reply code
     * @param enhancedCode the enhanced status code
     * @param text         the reply text
     * @return the reply
     */
    public static SmtpReply of(int code, EnhancedStatusCode enhancedCode, String text) {
        return new SmtpReply(code, enhancedCode, text);
    }

    /**
     * Creates a multiline reply.
     *
     * @param code  the reply code
     * @param lines the reply lines
     * @return the reply
     */
    public static SmtpReply ofLines(int code, List<String> lines) {
        return new SmtpReply(code, null, lines);
    }

    /**
     * Creates a multiline reply with an enhanced status code.
     *
     * @param code         the reply code
     * @param enhancedCode the enhanced status code
     * @param lines        the reply lines
     * @return the reply
     */
    public static SmtpReply ofLines(int code, EnhancedStatusCode enhancedCode, List<String> lines) {
        return new SmtpReply(code, enhancedCode, lines);
    }

    // --- Standard replies ---

    /** 220 greeting. */
    public static SmtpReply greeting(String hostname) {
        return new SmtpReply(220, hostname + " ESMTP ready");
    }

    /** 221 closing connection. */
    public static SmtpReply closing(String hostname) {
        return new SmtpReply(221, EnhancedStatusCode.SUCCESS_OTHER, hostname + " closing connection");
    }

    /** 235 authentication successful. */
    public static SmtpReply authSuccess() {
        return new SmtpReply(235, EnhancedStatusCode.SUCCESS_AUTH, "Authentication successful");
    }

    /** 250 OK. */
    public static SmtpReply ok() {
        return new SmtpReply(250, EnhancedStatusCode.SUCCESS_OTHER, "OK");
    }

    /** 250 OK with message ID. */
    public static SmtpReply ok(String messageId) {
        return new SmtpReply(250, EnhancedStatusCode.SUCCESS_MAIL_SYSTEM, "OK id=" + messageId);
    }

    /** 250 sender OK. */
    public static SmtpReply senderOk() {
        return new SmtpReply(250, EnhancedStatusCode.SUCCESS_ADDRESS, "Sender OK");
    }

    /** 250 recipient OK. */
    public static SmtpReply recipientOk() {
        return new SmtpReply(250, EnhancedStatusCode.SUCCESS_DEST_VALID, "Recipient OK");
    }

    /** 334 auth challenge. */
    public static SmtpReply authChallenge(String challenge) {
        return new SmtpReply(334, challenge);
    }

    /** 354 start mail input. */
    public static SmtpReply startInput() {
        return new SmtpReply(354, "Start mail input; end with <CRLF>.<CRLF>");
    }

    /** 421 service not available. */
    public static SmtpReply serviceUnavailable(String hostname) {
        return new SmtpReply(421, EnhancedStatusCode.TRANS_SERVICE_UNAVAIL,
                hostname + " Service not available, closing transmission channel");
    }

    /** 450 mailbox unavailable. */
    public static SmtpReply mailboxBusy() {
        return new SmtpReply(450, EnhancedStatusCode.TRANS_MAILBOX_BUSY,
                "Requested mail action not taken: mailbox unavailable");
    }

    /** 500 syntax error, command unrecognized. */
    public static SmtpReply commandUnrecognized() {
        return new SmtpReply(500, EnhancedStatusCode.PERM_COMMAND_UNRECOGNIZED,
                "Syntax error, command unrecognized");
    }

    /** 501 syntax error in parameters or arguments. */
    public static SmtpReply syntaxError() {
        return new SmtpReply(501, EnhancedStatusCode.PERM_SYNTAX_ERROR,
                "Syntax error in parameters or arguments");
    }

    /** 502 command not implemented. */
    public static SmtpReply notImplemented() {
        return new SmtpReply(502, EnhancedStatusCode.PERM_PARAM_NOT_IMPL,
                "Command not implemented");
    }

    /** 503 bad sequence of commands. */
    public static SmtpReply badSequence() {
        return new SmtpReply(503, EnhancedStatusCode.PERM_BAD_SEQUENCE,
                "Bad sequence of commands");
    }

    /** 530 authentication required. */
    public static SmtpReply authRequired() {
        return new SmtpReply(530, EnhancedStatusCode.PERM_AUTH_REQUIRED,
                "Authentication required");
    }

    /** 535 authentication credentials invalid. */
    public static SmtpReply authFailed() {
        return new SmtpReply(535, EnhancedStatusCode.PERM_AUTH_INVALID,
                "Authentication credentials invalid");
    }

    /** 550 mailbox not found. */
    public static SmtpReply mailboxNotFound() {
        return new SmtpReply(550, EnhancedStatusCode.PERM_BAD_DEST_MAILBOX,
                "Requested action not taken: mailbox unavailable");
    }

    /** 552 message too large. */
    public static SmtpReply messageTooLarge() {
        return new SmtpReply(552, EnhancedStatusCode.PERM_MSG_TOO_BIG,
                "Requested mail action aborted: exceeded storage allocation");
    }

    /** 553 mailbox name not allowed. */
    public static SmtpReply mailboxSyntaxError() {
        return new SmtpReply(553, EnhancedStatusCode.PERM_BAD_DEST_SYNTAX,
                "Requested action not taken: mailbox name not allowed");
    }

    /** 554 transaction failed. */
    public static SmtpReply transactionFailed() {
        return new SmtpReply(554, EnhancedStatusCode.PERM_REFUSED,
                "Transaction failed");
    }

    // --- Accessors ---

    /**
     * Returns the three-digit reply code.
     *
     * @return the reply code
     */
    public int code() {
        return code;
    }

    /**
     * Returns the enhanced status code, if present.
     *
     * @return the enhanced status code, or {@code null}
     */
    public EnhancedStatusCode enhancedCode() {
        return enhancedCode;
    }

    /**
     * Returns the first line of text.
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
     * Returns {@code true} if this is a transient negative (4xx).
     *
     * @return true for transient failure replies
     */
    public boolean isTransientFailure() {
        return code >= 400 && code < 500;
    }

    /**
     * Returns {@code true} if this is a permanent negative (5xx).
     *
     * @return true for permanent failure replies
     */
    public boolean isPermanentFailure() {
        return code >= 500;
    }

    /**
     * Returns {@code true} if this is any negative reply (4xx or 5xx).
     *
     * @return true for error replies
     */
    public boolean isNegative() {
        return code >= 400;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(code);
            sb.append(i < lines.size() - 1 ? '-' : ' ');
            if (enhancedCode != null && (i == 0 || i == lines.size() - 1)) {
                sb.append(enhancedCode.wireForm()).append(' ');
            }
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
        if (!(obj instanceof SmtpReply other)) return false;
        return code == other.code
                && Objects.equals(enhancedCode, other.enhancedCode)
                && lines.equals(other.lines);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, enhancedCode, lines);
    }
}
