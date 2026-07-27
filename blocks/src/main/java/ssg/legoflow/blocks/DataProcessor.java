package ssg.legoflow.blocks;

public interface DataProcessor<I, O> extends AutoCloseable {

    @SuppressWarnings("unchecked")
    void consume(Context ctx, I... data);

    @SuppressWarnings("unchecked")
    void produce(Context ctx, I... data);

    @SuppressWarnings("unchecked")
    void accept(Context ctx, O... data);

    @SuppressWarnings("unchecked")
    void submit(Context ctx, O... data);

    ProcessorState getState();

    void addStateListener(StateListener listener);

    void removeStateListener(StateListener listener);

    ProcessorStatistics getStatistics();

    void addInputFilter(DataFilter<I> filter);

    void addOutputFilter(DataFilter<O> filter);

    @Override
    void close();
}
