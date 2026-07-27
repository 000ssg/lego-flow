package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FilterChainDemoTest {

    @Test
    void testMultipleInputFiltersInChain() {
        // Given: a processor with validation → transform → logging filters
        var results = new ArrayList<String>();
        var processor = new PassthroughProcessor<>(String.class) {
            @Override
            public void accept(Context ctx, String... data) {
                super.accept(ctx, data);
                results.addAll(List.of(data));
            }
        };
        processor.addInputFilter(new ValidationFilter<>(String.class, s -> !s.isBlank()));
        processor.addInputFilter(new TransformFilter<>(String.class, String::trim));
        processor.addInputFilter(new TransformFilter<>(String.class, String::toUpperCase));
        var ctx = new DefaultContext();

        // When: consuming data with blanks and whitespace
        processor.consume(ctx, "hello", "", " world ", "  ");

        // Then: blanks rejected, remaining trimmed and uppercased
        assertThat(results).containsExactly("HELLO", "WORLD");
    }

    @Test
    void testOutputFiltersAppliedOnConsume() {
        // Given: a String→Integer processor with output filter (only evens)
        var results = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                results.addAll(List.of(data));
            }
        };
        processor.addOutputFilter(new ValidationFilter<>(Integer.class, i -> i % 2 == 0));
        var ctx = new DefaultContext();

        // When: consuming a mix
        processor.consume(ctx, "1", "2", "3", "4", "5");

        // Then: only even numbers pass the output filter
        assertThat(results).containsExactly(2, 4);
    }

    @Test
    void testInputAndOutputFiltersCombined() {
        // Given: filters on both sides
        var results = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                results.addAll(List.of(data));
            }
        };
        processor.addInputFilter(new ValidationFilter<>(String.class, s -> !s.startsWith("-")));
        processor.addOutputFilter(new ValidationFilter<>(Integer.class, i -> i < 100));
        var ctx = new DefaultContext();

        // When: consuming
        processor.consume(ctx, "5", "-10", "200", "42");

        // Then: negative strings rejected by input filter, >100 by output filter
        assertThat(results).containsExactly(5, 42);
    }

    @Test
    void testFilterStatisticsInChain() {
        // Given: a validation filter used in a processor
        var filter = new ValidationFilter<>(String.class, s -> s.length() >= 3);
        var processor = new PassthroughProcessor<>(String.class);
        processor.addInputFilter(filter);
        var ctx = new DefaultContext();

        // When: consuming data
        processor.consume(ctx, "hi", "hello", "yo", "world", "a");

        // Then: filter statistics reflect its own in/out counts
        assertThat(filter.getStatistics().getInCount(String.class)).isEqualTo(5);
        assertThat(filter.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }
}
