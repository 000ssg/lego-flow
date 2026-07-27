package ssg.legoflow.media.sip.dialog;

/**
 * SIP dialog states per RFC 3261 section 12.
 *
 * <p>A dialog represents a peer-to-peer SIP relationship between two
 * user agents that persists for some time. Dialogs are identified by
 * Call-ID, local tag, and remote tag.
 *
 * @since 1.0.0
 */
public enum DialogState {

    /**
     * Early dialog: created by a provisional response (1xx) with a To tag
     * to an INVITE request.
     */
    EARLY,

    /**
     * Confirmed dialog: created by a 2xx response to an INVITE request,
     * or transitioned from EARLY state.
     */
    CONFIRMED,

    /**
     * Terminated dialog: ended by a BYE request/response or error.
     */
    TERMINATED;

    /**
     * Returns true if the dialog is still active (EARLY or CONFIRMED).
     *
     * @return true if active
     * @since 1.0.0
     */
    public boolean isActive() {
        return this == EARLY || this == CONFIRMED;
    }
}
