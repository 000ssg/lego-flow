package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class StringToIntConverterDemoTest {

    @Test
    void testBasicConversion() {
        // Given: a String→Integer processor
        var results = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                results.addAll(List.of(data));
            }
        };
        var ctx = new DefaultContext();

        // When: consuming numeric strings
        processor.consume(ctx, "1", "42", "100");

        // Then: integers arrive at accept
        assertThat(results).containsExactly(1, 42, 100);
    }

    @Test
    void testConversionWithInvalidDataSkipsGracefully() {
        // Given: a processor
        var results = new ArrayList<Integer>();
        var processor = new StringToIntProcessor() {
            @Override
            public void accept(Context ctx, Integer... data) {
                super.accept(ctx, data);
                results.addAll(List.of(data));
            }
        };
        var ctx = new DefaultContext();

        // When: consuming mixed valid/invalid strings
        processor.consume(ctx, "10", "not-a-number", "20", "abc");

        // Then: only valid conversions arrive
        assertThat(results).containsExactly(10, 20);
    }

    @Test
    void testReverseConversionInSubmit() {
        // Given: a processor
        var produced = new ArrayList<String>();
        var processor = new StringToIntProcessor() {
            @Override
            public void produce(Context ctx, String... data) {
                super.produce(ctx, data);
                produced.addAll(List.of(data));
            }
        };
        var ctx = new DefaultContext();

        // When: submitting integers
        processor.submit(ctx, 42, 7);

        // Then: strings arrive at produce
        assertThat(produced).containsExactly("42", "7");
    }

    @Test
    void testStatisticsAfterConversion() {
        // Given: a processor
        var processor = new StringToIntProcessor();
        var ctx = new DefaultContext();

        // When: consuming data
        processor.consume(ctx, "1", "2", "3");

        // Then: statistics reflect input
        assertThat(processor.getStatistics().getInCount(String.class)).isEqualTo(3);
    }
}
