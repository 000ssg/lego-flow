package ssg.legoflow.http2.stream;

import java.util.Set;

public enum Http2StreamState {

    IDLE,
    RESERVED_LOCAL,
    RESERVED_REMOTE,
    OPEN,
    HALF_CLOSED_LOCAL,
    HALF_CLOSED_REMOTE,
    CLOSED;

    private static final Set<Http2StreamState> IDLE_TRANSITIONS = Set.of(OPEN, RESERVED_LOCAL, RESERVED_REMOTE);
    private static final Set<Http2StreamState> RESERVED_LOCAL_TRANSITIONS = Set.of(HALF_CLOSED_REMOTE, CLOSED);
    private static final Set<Http2StreamState> RESERVED_REMOTE_TRANSITIONS = Set.of(HALF_CLOSED_LOCAL, CLOSED);
    private static final Set<Http2StreamState> OPEN_TRANSITIONS = Set.of(HALF_CLOSED_LOCAL, HALF_CLOSED_REMOTE, CLOSED);
    private static final Set<Http2StreamState> HALF_CLOSED_LOCAL_TRANSITIONS = Set.of(CLOSED);
    private static final Set<Http2StreamState> HALF_CLOSED_REMOTE_TRANSITIONS = Set.of(CLOSED);

    public boolean canTransitionTo(Http2StreamState target) {
        if (this == target) return false;
        return switch (this) {
            case IDLE -> IDLE_TRANSITIONS.contains(target);
            case RESERVED_LOCAL -> RESERVED_LOCAL_TRANSITIONS.contains(target);
            case RESERVED_REMOTE -> RESERVED_REMOTE_TRANSITIONS.contains(target);
            case OPEN -> OPEN_TRANSITIONS.contains(target);
            case HALF_CLOSED_LOCAL -> HALF_CLOSED_LOCAL_TRANSITIONS.contains(target);
            case HALF_CLOSED_REMOTE -> HALF_CLOSED_REMOTE_TRANSITIONS.contains(target);
            case CLOSED -> false;
        };
    }
}
