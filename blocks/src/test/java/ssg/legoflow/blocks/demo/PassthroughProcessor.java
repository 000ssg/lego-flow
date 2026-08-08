package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.AbstractDataProcessor;
import ssg.legoflow.blocks.Context;

@SuppressWarnings("unchecked")
public class PassthroughProcessor<T> extends AbstractDataProcessor<T, T> {

    public PassthroughProcessor(Class<T> type) {
        super(type, type);
    }

    @Override
    protected T[] convertToOutput(Context ctx, T... input) {
        return input;
    }

    @Override
    protected T[] convertToInput(Context ctx, T... output) {
        return output;
    }
}
