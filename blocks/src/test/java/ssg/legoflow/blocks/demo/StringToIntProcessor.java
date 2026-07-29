package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.AbstractDataProcessor;
import ssg.legoflow.blocks.Context;

import java.util.Arrays;

@SuppressWarnings("unchecked")
public class StringToIntProcessor extends AbstractDataProcessor<String, Integer> {

    public StringToIntProcessor() {
        super(String.class, Integer.class);
    }

    @Override
    protected Integer[] convertToOutput(Context ctx, String... input) {
        return Arrays.stream(input)
                .map(s -> {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        ctx.handleError(e);
                        return null;
                    }
                })
                .filter(i -> i != null)
                .toArray(Integer[]::new);
    }

    @Override
    protected String[] convertToInput(Context ctx, Integer... output) {
        return Arrays.stream(output)
                .map(String::valueOf)
                .toArray(String[]::new);
    }
}
