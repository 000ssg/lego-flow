package ssg.legoflow.http3.quic;

import java.util.Set;

/**
 * QUIC stream states forming a state machine per RFC 9000 section 3.
 *
 * <p>Stream states track the sending and receiving sides independently.
 * Each state defines which transitions are valid via
 * {@link #validTransitions()} and {@link #canTransitionTo(QuicStreamState)}.</p>
 *
 * @since 0.1.0
 */
public enum QuicStreamState {

    /** Stream has not been created or opened yet. */
    IDLE,

    /** Both sending and receiving are active. */
    OPEN,

    /** Local side has finished sending. */
    HALF_CLOSED_LOCAL,

    /** Remote side has finished sending. */
    HALF_CLOSED_REMOTE,

    /** Stream is fully closed. */
    CLOSED,

    /** A RESET_STREAM frame has been sent. */
    RESET_SENT,

    /** A RESET_STREAM frame has been received. */
    RESET_RECEIVED;

    private static final Set<QuicStreamState> IDLE_TRANSITIONS =
            Set.of(OPEN, HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE);
    private static final Set<QuicStreamState> OPEN_TRANSITIONS =
            Set.of(HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED, RESET_SENT, RESET_RECEIVED);
    private static final Set<QuicStreamState> HALF_CLOSED_LOCAL_TRANSITIONS =
            Set.of(CLOSED, RESET_RECEIVED);
    private static final Set<QuicStreamState> HALF_CLOSED_REMOTE_TRANSITIONS =
            Set.of(CLOSED, RESET_SENT);
    private static final Set<QuicStreamState> RESET_SENT_TRANSITIONS =
            Set.of(CLOSED);
    private static final Set<QuicStreamState> RESET_RECEIVED_TRANSITIONS =
            Set.of(CLOSED);

    /**
     * Returns the set of states that this state can transition to.
     *
     * @return an unmodifiable set of valid target states
     * @since 0.1.0
     */
    public Set<QuicStreamState> validTransitions() {
        return switch (this) {
            case IDLE -> IDLE_TRANSITIONS;
            case OPEN -> OPEN_TRANSITIONS;
            case HALF_CLOSED_LOCAL -> HALF_CLOSED_LOCAL_TRANSITIONS;
            case HALF_CLOSED_REMOTE -> HALF_CLOSED_REMOTE_TRANSITIONS;
            case RESET_SENT -> RESET_SENT_TRANSITIONS;
            case RESET_RECEIVED -> RESET_RECEIVED_TRANSITIONS;
            case CLOSED -> Set.of();
        };
    }

    /**
     * Checks whether a transition to the given target state is valid.
     *
     * @param target the desired target state
     * @return {@code true} if the transition is permitted
     * @since 0.1.0
     */
    public boolean canTransitionTo(QuicStreamState target) {
        if (this == target) return false;
        return validTransitions().contains(target);
    }
}
