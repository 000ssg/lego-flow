package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.UnaryOperator;

@SuppressWarnings("unchecked")
public class TransformFilter<T> extends AbstractDataFilter<T> {

    private final UnaryOperator<T> transform;

    public TransformFilter(Class<T> type, UnaryOperator<T> transform) {
        super(type);
        this.transform = transform;
    }

    @Override
    protected T[] doFilter(Context ctx, T... data) {
        return Arrays.stream(data)
                .map(transform)
                .toArray(size -> (T[]) Array.newInstance(data.getClass().getComponentType(), size));
    }
}
