package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class LoggingFilter<T> extends AbstractDataFilter<T> {

    private final String label;

    public LoggingFilter(Class<T> type, String label) {
        super(type);
        this.label = label;
    }

    @Override
    protected T[] doFilter(Context ctx, T... data) {
        ctx.getLogger().info("[{}] Passing {} items: {}", label, data.length, Arrays.toString(data));
        return data;
    }
}
