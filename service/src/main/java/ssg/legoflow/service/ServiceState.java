package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorState;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum ServiceState {

    IDLE(ProcessorState.IDLE),
    CONNECTING_TRANSPORT(ProcessorState.CONNECTING),
    AUTHENTICATING(ProcessorState.CONNECTING),
    READY(ProcessorState.READY),
    PAUSED(ProcessorState.PAUSED),
    DRAINING(ProcessorState.READY),
    DISCONNECTING(ProcessorState.READY),
    FAILED(ProcessorState.FAILED),
    STOPPED(ProcessorState.STOPPED);

    private static final Map<ServiceState, Set<ServiceState>> VALID_TRANSITIONS = Map.of(
            IDLE, EnumSet.of(CONNECTING_TRANSPORT, READY, STOPPED),
            CONNECTING_TRANSPORT, EnumSet.of(AUTHENTICATING, READY, FAILED, STOPPED),
            AUTHENTICATING, EnumSet.of(READY, FAILED, STOPPED),
            READY, EnumSet.of(PAUSED, DRAINING, DISCONNECTING, FAILED, STOPPED),
            PAUSED, EnumSet.of(READY, DRAINING, FAILED, STOPPED),
            DRAINING, EnumSet.of(DISCONNECTING, FAILED, STOPPED),
            DISCONNECTING, EnumSet.of(IDLE, FAILED, STOPPED),
            FAILED, EnumSet.of(CONNECTING_TRANSPORT, IDLE, STOPPED),
            STOPPED, EnumSet.noneOf(ServiceState.class)
    );

    private final ProcessorState processorState;

    ServiceState(ProcessorState processorState) {
        this.processorState = processorState;
    }

    public ProcessorState toProcessorState() {
        return processorState;
    }

    public boolean canTransitionTo(ServiceState target) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }
}
