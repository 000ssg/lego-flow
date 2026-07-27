package ssg.legoflow.blocks.demo;

import ssg.legoflow.blocks.DefaultContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsAggregationDemoTest {

    @Test
    void testMultiTypeStatisticsTracking() {
        // Given: a String→Integer processor
        var processor = new StringToIntProcessor();
        var ctx = new DefaultContext();

        // When: consuming strings that become integers
        processor.consume(ctx, "1", "2", "3");

        // Then: input type (String) counted on consume, output type (Integer) counted on consume exit
        var stats = processor.getStatistics();
        assertThat(stats.getInCount(String.class)).isEqualTo(3);
        assertThat(stats.getOutCount(Integer.class)).isEqualTo(3);
    }

    @Test
    void testFilterRejectionReflectedInStats() {
        // Given: a processor with a filter that rejects half the data
        var filter = new ValidationFilter<>(String.class, s -> s.startsWith("a"));
        var processor = new PassthroughProcessor<>(String.class);
        processor.addInputFilter(filter);
        var ctx = new DefaultContext();

        // When: consuming mixed data
        processor.consume(ctx, "apple", "banana", "avocado", "cherry");

        // Then: filter stats show 4 in, 2 out; processor stats show 4 input items
        assertThat(filter.getStatistics().getInCount(String.class)).isEqualTo(4);
        assertThat(filter.getStatistics().getOutCount(String.class)).isEqualTo(2);
        assertThat(processor.getStatistics().getInCount(String.class)).isEqualTo(4);
        // After filtering, only 2 items pass through to output
        assertThat(processor.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }

    @Test
    void testSnapshotIsImmutable() {
        // Given: a processor with some activity
        var processor = new PassthroughProcessor<>(String.class);
        var ctx = new DefaultContext();
        processor.consume(ctx, "a", "b");

        // When: taking a snapshot and then consuming more
        var snapshot = processor.getStatistics().snapshot();
        processor.consume(ctx, "c");

        // Then: snapshot is frozen at the point it was taken
        assertThat(snapshot.inCounts().get(String.class.getName())).isEqualTo(2L);
        // After consuming one more, live stats are 3
        assertThat(processor.getStatistics().getInCount(String.class)).isEqualTo(3);
    }

    @Test
    void testSubmitStatisticsTracking() {
        // Given: a processor
        var processor = new StringToIntProcessor();
        var ctx = new DefaultContext();

        // When: submitting integers (reverse direction)
        processor.submit(ctx, 10, 20);

        // Then: output type (Integer) counted as input on submit entry, and String counted as output via produce
        assertThat(processor.getStatistics().getInCount(Integer.class)).isEqualTo(2);
        assertThat(processor.getStatistics().getOutCount(String.class)).isEqualTo(2);
    }
}
