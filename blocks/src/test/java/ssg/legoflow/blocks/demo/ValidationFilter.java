package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("unchecked")
public class ValidationFilter<T> extends AbstractDataFilter<T> {

    private final Predicate<T> validator;

    public ValidationFilter(Class<T> type, Predicate<T> validator) {
        super(type);
        this.validator = validator;
    }

    @Override
    protected T[] doFilter(Context ctx, T... data) {
        return Arrays.stream(data)
                .filter(item -> {
                    boolean valid = validator.test(item);
                    if (!valid) {
                        ctx.getLogger().debug("Validation rejected: {}", item);
                    }
                    return valid;
                })
                .toArray(size -> (T[]) Array.newInstance(data.getClass().getComponentType(), size));
    }
}
