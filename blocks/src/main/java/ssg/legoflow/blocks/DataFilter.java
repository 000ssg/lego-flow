package ssg.legoflow.blocks;

public interface DataFilter<T> extends AutoCloseable {

    @SuppressWarnings("unchecked")
    T[] filter(Context ctx, T... data);

    ProcessorState getState();

    ProcessorStatistics getStatistics();

    @Override
    void close();
}
