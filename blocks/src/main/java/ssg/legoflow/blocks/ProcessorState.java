package ssg.legoflow.blocks;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ProcessorState {
    IDLE,
    CONNECTING,
    READY,
    PAUSED,
    FAILED,
    STOPPED;

    private static final Map<ProcessorState, Set<ProcessorState>> VALID_TRANSITIONS = Map.of(
            IDLE, EnumSet.of(CONNECTING, READY, STOPPED),
            CONNECTING, EnumSet.of(READY, FAILED, STOPPED),
            READY, EnumSet.of(PAUSED, FAILED, STOPPED),
            PAUSED, EnumSet.of(READY, FAILED, STOPPED),
            FAILED, EnumSet.of(CONNECTING, READY, STOPPED),
            STOPPED, EnumSet.noneOf(ProcessorState.class)
    );

    public boolean canTransitionTo(ProcessorState target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
