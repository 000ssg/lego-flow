package ssg.legoflow.blocks.exceptions;

import ssg.legoflow.blocks.ProcessorState;

public class StateTransitionException extends ProcessorException {

    private final ProcessorState from;
    private final ProcessorState to;

    public StateTransitionException(ProcessorState from, ProcessorState to) {
        super("Invalid state transition: " + from + " → " + to);
        this.from = from;
        this.to = to;
    }

    public ProcessorState getFrom() {
        return from;
    }

    public ProcessorState getTo() {
        return to;
    }
}
