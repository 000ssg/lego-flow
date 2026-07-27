package ssg.legoflow.blocks;

@FunctionalInterface
public interface StateListener {

    void onStateChanged(ProcessorState oldState, ProcessorState newState);
}
