package ssg.legoflow.blocks;

import ssg.legoflow.blocks.exceptions.StateTransitionException;

import java.lang.reflect.Array;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractDataProcessor<I, O> implements DataProcessor<I, O> {

    private final AtomicReference<ProcessorState> state = new AtomicReference<>(ProcessorState.IDLE);
    private final ProcessorStatistics statistics = new ProcessorStatistics();
    private final List<StateListener> stateListeners = new CopyOnWriteArrayList<>();
    private final List<DataFilter<I>> inputFilters = new CopyOnWriteArrayList<>();
    private final List<DataFilter<O>> outputFilters = new CopyOnWriteArrayList<>();
    private final Class<I> inputType;
    private final Class<O> outputType;

    protected AbstractDataProcessor(Class<I> inputType, Class<O> outputType) {
        this.inputType = inputType;
        this.outputType = outputType;
    }

    @SuppressWarnings("unchecked")
    protected abstract O[] convertToOutput(Context ctx, I... input);

    @SuppressWarnings("unchecked")
    protected abstract I[] convertToInput(Context ctx, O... output);

    @SuppressWarnings("unchecked")
    @Override
    public void consume(Context ctx, I... data) {
        statistics.recordIn(inputType, data.length, data.length);

        I[] filtered = applyFilters(inputFilters, ctx, data, inputType);
        if (filtered == null || filtered.length == 0) return;

        O[] converted = convertToOutput(ctx, filtered);
        if (converted == null || converted.length == 0) return;

        O[] outputFiltered = applyFilters(outputFilters, ctx, converted, outputType);
        if (outputFiltered == null || outputFiltered.length == 0) return;

        statistics.recordOut(outputType, outputFiltered.length, outputFiltered.length);
        accept(ctx, outputFiltered);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void produce(Context ctx, I... data) {
        statistics.recordOut(inputType, data.length, data.length);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void accept(Context ctx, O... data) {
        // accept is the local receive endpoint — stats already recorded by consume path
    }

    @SuppressWarnings("unchecked")
    @Override
    public void submit(Context ctx, O... data) {
        statistics.recordIn(outputType, data.length, data.length);

        O[] outputFiltered = applyFilters(outputFilters, ctx, data, outputType);
        if (outputFiltered == null || outputFiltered.length == 0) return;

        I[] converted = convertToInput(ctx, outputFiltered);
        if (converted == null || converted.length == 0) return;

        I[] filtered = applyFilters(inputFilters, ctx, converted, inputType);
        if (filtered == null || filtered.length == 0) return;

        produce(ctx, filtered);
    }

    @Override
    public ProcessorState getState() {
        return state.get();
    }

    protected void transitionTo(ProcessorState newState) {
        var oldState = state.get();
        if (!oldState.canTransitionTo(newState)) {
            throw new StateTransitionException(oldState, newState);
        }
        state.set(newState);
        for (var listener : stateListeners) {
            listener.onStateChanged(oldState, newState);
        }
    }

    @Override
    public void addStateListener(StateListener listener) {
        stateListeners.add(listener);
    }

    @Override
    public void removeStateListener(StateListener listener) {
        stateListeners.remove(listener);
    }

    @Override
    public ProcessorStatistics getStatistics() {
        return statistics;
    }

    @Override
    public void addInputFilter(DataFilter<I> filter) {
        inputFilters.add(filter);
    }

    @Override
    public void addOutputFilter(DataFilter<O> filter) {
        outputFilters.add(filter);
    }

    @Override
    public void close() {
        var current = state.get();
        if (current != ProcessorState.STOPPED) {
            var old = state.getAndSet(ProcessorState.STOPPED);
            for (var listener : stateListeners) {
                listener.onStateChanged(old, ProcessorState.STOPPED);
            }
        }
        for (var filter : inputFilters) {
            filter.close();
        }
        for (var filter : outputFilters) {
            filter.close();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T[] applyFilters(List<DataFilter<T>> filters, Context ctx, T[] data, Class<T> type) {
        T[] current = data;
        for (var filter : filters) {
            current = filter.filter(ctx, current);
            if (current == null || current.length == 0) {
                return emptyArray(type);
            }
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] emptyArray(Class<T> type) {
        return (T[]) Array.newInstance(type, 0);
    }
}
