package ssg.legoflow.email.imap.protocol;

/**
 * IMAP response status codes per RFC 9051.
 *
 * @since 1.0.0
 */
public enum ImapStatus {

    /** Command completed successfully. */
    OK("OK"),

    /** Command completed but with a warning or informational note. */
    NO("NO"),

    /** Protocol error or unknown command. */
    BAD("BAD"),

    /** Server is shutting down the connection. */
    BYE("BYE"),

    /** Pre-authenticated connection (no LOGIN needed). */
    PREAUTH("PREAUTH");

    private final String text;

    ImapStatus(String text) {
        this.text = text;
    }

    /**
     * Returns the protocol text for this status.
     *
     * @return the status string
     */
    public String text() {
        return text;
    }

    /**
     * Parses a status string (case-insensitive).
     *
     * @param text the status text
     * @return the matching status
     * @throws IllegalArgumentException if no status matches
     */
    public static ImapStatus parse(String text) {
        String upper = text.toUpperCase();
        for (ImapStatus s : values()) {
            if (s.text.equals(upper)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown IMAP status: " + text);
    }
}
