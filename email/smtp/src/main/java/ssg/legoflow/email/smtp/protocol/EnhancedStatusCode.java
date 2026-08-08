package ssg.legoflow.email.smtp.protocol;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Enhanced mail system status code per RFC 3463 and RFC 2034.
 *
 * <p>Format: {@code class.subject.detail} where:
 * <ul>
 *   <li>{@code class} -- 2 (success), 4 (persistent transient failure), 5 (permanent failure)</li>
 *   <li>{@code subject} -- 0-9 (address, mailbox, mail system, network, protocol, content, security, policy)</li>
 *   <li>{@code detail} -- 0-999 (specific condition)</li>
 * </ul>
 *
 * @param statusClass the class digit (2, 4, or 5)
 * @param subject     the subject digit (0-9)
 * @param detail      the detail number (0-999)
 * @since 0.1.0
 */
public record EnhancedStatusCode(int statusClass, int subject, int detail) {

    /** Success: other or undefined status. */
    public static final EnhancedStatusCode SUCCESS_OTHER = new EnhancedStatusCode(2, 0, 0);
    /** Success: other address status. */
    public static final EnhancedStatusCode SUCCESS_ADDRESS = new EnhancedStatusCode(2, 1, 0);
    /** Success: destination address valid. */
    public static final EnhancedStatusCode SUCCESS_DEST_VALID = new EnhancedStatusCode(2, 1, 5);
    /** Success: other mailbox status. */
    public static final EnhancedStatusCode SUCCESS_MAILBOX = new EnhancedStatusCode(2, 2, 0);
    /** Success: message accepted. */
    public static final EnhancedStatusCode SUCCESS_MAIL_SYSTEM = new EnhancedStatusCode(2, 6, 0);
    /** Success: authentication succeeded. */
    public static final EnhancedStatusCode SUCCESS_AUTH = new EnhancedStatusCode(2, 7, 0);

    /** Permanent failure: bad destination mailbox address. */
    public static final EnhancedStatusCode PERM_BAD_DEST_MAILBOX = new EnhancedStatusCode(5, 1, 1);
    /** Permanent failure: bad destination mailbox address syntax. */
    public static final EnhancedStatusCode PERM_BAD_DEST_SYNTAX = new EnhancedStatusCode(5, 1, 3);
    /** Permanent failure: message refused. */
    public static final EnhancedStatusCode PERM_REFUSED = new EnhancedStatusCode(5, 7, 1);
    /** Permanent failure: command not recognized. */
    public static final EnhancedStatusCode PERM_COMMAND_UNRECOGNIZED = new EnhancedStatusCode(5, 5, 1);
    /** Permanent failure: syntax error in parameters. */
    public static final EnhancedStatusCode PERM_SYNTAX_ERROR = new EnhancedStatusCode(5, 5, 2);
    /** Permanent failure: command parameter not implemented. */
    public static final EnhancedStatusCode PERM_PARAM_NOT_IMPL = new EnhancedStatusCode(5, 5, 4);
    /** Permanent failure: authentication required. */
    public static final EnhancedStatusCode PERM_AUTH_REQUIRED = new EnhancedStatusCode(5, 7, 0);
    /** Permanent failure: bad command sequence. */
    public static final EnhancedStatusCode PERM_BAD_SEQUENCE = new EnhancedStatusCode(5, 5, 1);
    /** Permanent failure: message too big. */
    public static final EnhancedStatusCode PERM_MSG_TOO_BIG = new EnhancedStatusCode(5, 3, 4);
    /** Permanent failure: authentication credentials invalid. */
    public static final EnhancedStatusCode PERM_AUTH_INVALID = new EnhancedStatusCode(5, 7, 8);
    /** Permanent failure: authentication mechanism too weak. */
    public static final EnhancedStatusCode PERM_AUTH_WEAK = new EnhancedStatusCode(5, 7, 9);

    /** Transient failure: mailbox busy. */
    public static final EnhancedStatusCode TRANS_MAILBOX_BUSY = new EnhancedStatusCode(4, 2, 1);
    /** Transient failure: service not available. */
    public static final EnhancedStatusCode TRANS_SERVICE_UNAVAIL = new EnhancedStatusCode(4, 0, 0);
    /** Transient failure: insufficient storage. */
    public static final EnhancedStatusCode TRANS_INSUFF_STORAGE = new EnhancedStatusCode(4, 3, 1);

    private static final Pattern FORMAT = Pattern.compile("([245])\\.([0-9]+)\\.([0-9]+)");

    /**
     * Constructs an enhanced status code with validation.
     *
     * @param statusClass the class digit (2, 4, or 5)
     * @param subject     the subject digit (0-9)
     * @param detail      the detail number (0-999)
     */
    public EnhancedStatusCode {
        if (statusClass != 2 && statusClass != 4 && statusClass != 5) {
            throw new IllegalArgumentException("Status class must be 2, 4, or 5, got: " + statusClass);
        }
        if (subject < 0 || subject > 9) {
            throw new IllegalArgumentException("Subject must be 0-9, got: " + subject);
        }
        if (detail < 0 || detail > 999) {
            throw new IllegalArgumentException("Detail must be 0-999, got: " + detail);
        }
    }

    /**
     * Parses an enhanced status code from its string representation.
     *
     * @param text the status code string (e.g., "2.1.0")
     * @return the parsed enhanced status code
     * @throws IllegalArgumentException if the format is invalid
     */
    public static EnhancedStatusCode parse(String text) {
        Objects.requireNonNull(text, "text");
        var matcher = FORMAT.matcher(text.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid enhanced status code: " + text);
        }
        return new EnhancedStatusCode(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        );
    }

    /**
     * Returns {@code true} if this is a success status (class 2).
     *
     * @return true for success
     */
    public boolean isSuccess() {
        return statusClass == 2;
    }

    /**
     * Returns {@code true} if this is a transient failure (class 4).
     *
     * @return true for transient failure
     */
    public boolean isTransientFailure() {
        return statusClass == 4;
    }

    /**
     * Returns {@code true} if this is a permanent failure (class 5).
     *
     * @return true for permanent failure
     */
    public boolean isPermanentFailure() {
        return statusClass == 5;
    }

    /**
     * Returns the wire format of this enhanced status code.
     *
     * @return the status code string (e.g., "2.1.0")
     */
    public String wireForm() {
        return statusClass + "." + subject + "." + detail;
    }

    @Override
    public String toString() {
        return wireForm();
    }
}
