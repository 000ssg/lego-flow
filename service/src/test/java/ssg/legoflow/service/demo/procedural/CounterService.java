package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;

import java.util.concurrent.atomic.AtomicInteger;

public class CounterService extends AbstractService<String, Integer> {

    private static final String COUNTER_KEY = "counter";

    public CounterService() {
        super(String.class, Integer.class, new ServiceDescriptor("counter", "Stateful counter with session scope"));
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Integer[] convertToOutput(Context ctx, String... input) {
        var result = new Integer[input.length];
        for (int i = 0; i < input.length; i++) {
            result[i] = switch (input[i]) {
                case "increment" -> incrementCounter(ctx);
                case "decrement" -> decrementCounter(ctx);
                case "get" -> getCounter(ctx);
                case "reset" -> resetCounter(ctx);
                default -> getCounter(ctx);
            };
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToInput(Context ctx, Integer... output) {
        var result = new String[output.length];
        for (int i = 0; i < output.length; i++) {
            result[i] = String.valueOf(output[i]);
        }
        return result;
    }

    private int incrementCounter(Context ctx) {
        return getOrCreateCounter(ctx).incrementAndGet();
    }

    private int decrementCounter(Context ctx) {
        return getOrCreateCounter(ctx).decrementAndGet();
    }

    private int getCounter(Context ctx) {
        return getOrCreateCounter(ctx).get();
    }

    private int resetCounter(Context ctx) {
        getOrCreateCounter(ctx).set(0);
        return 0;
    }

    private AtomicInteger getOrCreateCounter(Context ctx) {
        if (ctx instanceof ServiceContext sctx) {
            var counter = sctx.getSessionScope().<AtomicInteger>getAttribute(COUNTER_KEY);
            if (counter == null) {
                counter = new AtomicInteger(0);
                sctx.getSessionScope().setAttribute(COUNTER_KEY, counter);
            }
            return counter;
        }
        AtomicInteger counter = ctx.getAttribute(COUNTER_KEY);
        if (counter == null) {
            counter = new AtomicInteger(0);
            ctx.setAttribute(COUNTER_KEY, counter);
        }
        return counter;
    }
}
