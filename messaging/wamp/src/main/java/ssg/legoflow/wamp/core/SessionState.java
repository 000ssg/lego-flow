package ssg.legoflow.wamp.core;

/**
 * Lifecycle states of a WAMP session.
 *
 * <p>Transition: PENDING → ESTABLISHED → CLOSING → CLOSED</p>
 *
 * @since 0.2.0
 */
public enum SessionState {
    /** Session not yet established (before WELCOME sent). */
    PENDING,

    /** Session is active and fully established. */
    ESTABLISHED,

    /** Session is in the process of closing (GOODBYE sent, waiting for response). */
    CLOSING,

    /** Session is fully closed and resources are released. */
    CLOSED;

    /**
     * Returns {@code true} if this state represents an active session.
     *
     * @return {@code true} for ESTABLISHED or CLOSING
     */
    public boolean isActive() {
        return this == ESTABLISHED || this == CLOSING;
    }
}
