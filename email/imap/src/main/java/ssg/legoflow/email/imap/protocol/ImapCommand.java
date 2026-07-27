package ssg.legoflow.email.imap.protocol;

/**
 * IMAP4rev2 commands organized by connection state.
 *
 * <p>Each command knows which connection state(s) it is valid in,
 * enabling the server to reject commands issued in the wrong state.
 *
 * @since 1.0.0
 */
public enum ImapCommand {

    // Any state
    CAPABILITY("CAPABILITY", ImapState.ANY),
    NOOP("NOOP", ImapState.ANY),
    LOGOUT("LOGOUT", ImapState.ANY),

    // Not authenticated
    LOGIN("LOGIN", ImapState.NOT_AUTHENTICATED),
    AUTHENTICATE("AUTHENTICATE", ImapState.NOT_AUTHENTICATED),
    STARTTLS("STARTTLS", ImapState.NOT_AUTHENTICATED),

    // Authenticated
    SELECT("SELECT", ImapState.AUTHENTICATED),
    EXAMINE("EXAMINE", ImapState.AUTHENTICATED),
    CREATE("CREATE", ImapState.AUTHENTICATED),
    DELETE("DELETE", ImapState.AUTHENTICATED),
    RENAME("RENAME", ImapState.AUTHENTICATED),
    SUBSCRIBE("SUBSCRIBE", ImapState.AUTHENTICATED),
    UNSUBSCRIBE("UNSUBSCRIBE", ImapState.AUTHENTICATED),
    LIST("LIST", ImapState.AUTHENTICATED),
    NAMESPACE("NAMESPACE", ImapState.AUTHENTICATED),
    STATUS("STATUS", ImapState.AUTHENTICATED),
    APPEND("APPEND", ImapState.AUTHENTICATED),
    IDLE("IDLE", ImapState.AUTHENTICATED),

    // Selected (also valid in authenticated for some, but primary state is selected)
    FETCH("FETCH", ImapState.SELECTED),
    STORE("STORE", ImapState.SELECTED),
    COPY("COPY", ImapState.SELECTED),
    MOVE("MOVE", ImapState.SELECTED),
    SEARCH("SEARCH", ImapState.SELECTED),
    SORT("SORT", ImapState.SELECTED),
    THREAD("THREAD", ImapState.SELECTED),
    EXPUNGE("EXPUNGE", ImapState.SELECTED),
    CLOSE("CLOSE", ImapState.SELECTED),
    UNSELECT("UNSELECT", ImapState.SELECTED),
    UID("UID", ImapState.SELECTED);

    private final String text;
    private final ImapState requiredState;

    ImapCommand(String text, ImapState requiredState) {
        this.text = text;
        this.requiredState = requiredState;
    }

    /**
     * Returns the IMAP protocol text for this command.
     *
     * @return the command string as used on the wire
     */
    public String text() {
        return text;
    }

    /**
     * Returns the minimum connection state required to issue this command.
     *
     * @return the required state
     */
    public ImapState requiredState() {
        return requiredState;
    }

    /**
     * Parses a command string (case-insensitive) into an {@link ImapCommand}.
     *
     * @param text the command text
     * @return the matching command
     * @throws IllegalArgumentException if no command matches
     */
    public static ImapCommand parse(String text) {
        String upper = text.toUpperCase();
        for (ImapCommand cmd : values()) {
            if (cmd.text.equals(upper)) {
                return cmd;
            }
        }
        throw new IllegalArgumentException("Unknown IMAP command: " + text);
    }

    /**
     * Connection states for IMAP sessions.
     */
    public enum ImapState {
        /** Command valid in any state. */
        ANY,
        /** Command valid only before authentication. */
        NOT_AUTHENTICATED,
        /** Command valid after authentication (and in selected state). */
        AUTHENTICATED,
        /** Command valid only when a mailbox is selected. */
        SELECTED
    }
}
