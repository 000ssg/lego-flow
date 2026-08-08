package ssg.legoflow.http3.quic;

import java.util.Set;

/**
 * QUIC connection states forming a state machine.
 *
 * <p>A connection progresses through these states during its lifecycle:
 * {@link #IDLE} → {@link #HANDSHAKING} → {@link #CONNECTED} →
 * {@link #CLOSING} → {@link #DRAINING} → {@link #CLOSED}.
 * Each state defines which transitions are valid.</p>
 *
 * @since 0.1.0
 */
public enum QuicConnectionState {

    /** No connection established. */
    IDLE,

    /** TLS 1.3 handshake in progress. */
    HANDSHAKING,

    /** Handshake complete, connection is active. */
    CONNECTED,

    /** Connection close initiated, waiting for peer acknowledgement. */
    CLOSING,

    /** Received close from peer, draining remaining packets. */
    DRAINING,

    /** Connection fully closed. */
    CLOSED;

    private static final Set<QuicConnectionState> IDLE_TRANSITIONS = Set.of(HANDSHAKING, CLOSED);
    private static final Set<QuicConnectionState> HANDSHAKING_TRANSITIONS = Set.of(CONNECTED, CLOSING, CLOSED);
    private static final Set<QuicConnectionState> CONNECTED_TRANSITIONS = Set.of(CLOSING, DRAINING, CLOSED);
    private static final Set<QuicConnectionState> CLOSING_TRANSITIONS = Set.of(DRAINING, CLOSED);
    private static final Set<QuicConnectionState> DRAINING_TRANSITIONS = Set.of(CLOSED);

    /**
     * Returns the set of states that this state can transition to.
     *
     * @return an unmodifiable set of valid target states
     * @since 0.1.0
     */
    public Set<QuicConnectionState> validTransitions() {
        return switch (this) {
            case IDLE -> IDLE_TRANSITIONS;
            case HANDSHAKING -> HANDSHAKING_TRANSITIONS;
            case CONNECTED -> CONNECTED_TRANSITIONS;
            case CLOSING -> CLOSING_TRANSITIONS;
            case DRAINING -> DRAINING_TRANSITIONS;
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
    public boolean canTransitionTo(QuicConnectionState target) {
        if (this == target) return false;
        return validTransitions().contains(target);
    }
}
