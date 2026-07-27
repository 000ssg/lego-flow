package ssg.legoflow.blocks;

import ssg.legoflow.blocks.exceptions.StateTransitionException;

import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractDataFilter<T> implements DataFilter<T> {

    private final AtomicReference<ProcessorState> state = new AtomicReference<>(ProcessorState.IDLE);
    private final ProcessorStatistics statistics = new ProcessorStatistics();
    private final Class<T> dataType;

    protected AbstractDataFilter(Class<T> dataType) {
        this.dataType = dataType;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T[] filter(Context ctx, T... data) {
        if (data == null || data.length == 0) {
            return data;
        }
        statistics.recordIn(dataType, data.length, data.length);
        T[] result = doFilter(ctx, data);
        int outCount = result != null ? result.length : 0;
        statistics.recordOut(dataType, outCount, outCount);
        return result;
    }

    @SuppressWarnings("unchecked")
    protected abstract T[] doFilter(Context ctx, T... data);

    @Override
    public ProcessorState getState() {
        return state.get();
    }

    @Override
    public ProcessorStatistics getStatistics() {
        return statistics;
    }

    protected void transitionTo(ProcessorState newState) {
        var current = state.get();
        if (!current.canTransitionTo(newState)) {
            throw new StateTransitionException(current, newState);
        }
        state.set(newState);
    }

    @Override
    public void close() {
        var current = state.get();
        if (current != ProcessorState.STOPPED) {
            state.set(ProcessorState.STOPPED);
        }
    }
}
