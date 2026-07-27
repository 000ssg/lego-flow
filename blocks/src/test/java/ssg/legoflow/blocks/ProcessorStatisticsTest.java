package ssg.legoflow.blocks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessorStatisticsTest {

    @Test
    void testRecordInCountsAndAmounts() {
        var stats = new ProcessorStatistics();

        stats.recordIn(String.class, 3, 100);
        stats.recordIn(String.class, 2, 50);

        assertThat(stats.getInCount(String.class)).isEqualTo(5);
        assertThat(stats.getInAmount(String.class)).isEqualTo(150);
    }

    @Test
    void testRecordOutCountsAndAmounts() {
        var stats = new ProcessorStatistics();

        stats.recordOut(Integer.class, 4, 200);

        assertThat(stats.getOutCount(Integer.class)).isEqualTo(4);
        assertThat(stats.getOutAmount(Integer.class)).isEqualTo(200);
    }

    @Test
    void testDifferentTypesTrackedSeparately() {
        var stats = new ProcessorStatistics();

        stats.recordIn(String.class, 5, 50);
        stats.recordIn(Integer.class, 3, 30);

        assertThat(stats.getInCount(String.class)).isEqualTo(5);
        assertThat(stats.getInCount(Integer.class)).isEqualTo(3);
    }

    @Test
    void testUnrecordedTypeReturnsZero() {
        var stats = new ProcessorStatistics();

        assertThat(stats.getInCount(String.class)).isZero();
        assertThat(stats.getOutCount(String.class)).isZero();
        assertThat(stats.getInAmount(String.class)).isZero();
        assertThat(stats.getOutAmount(String.class)).isZero();
    }

    @Test
    void testSnapshot() {
        var stats = new ProcessorStatistics();
        stats.recordIn(String.class, 10, 100);
        stats.recordOut(Integer.class, 5, 50);

        var snapshot = stats.snapshot();

        assertThat(snapshot.inCounts()).containsEntry(String.class.getName(), 10L);
        assertThat(snapshot.outCounts()).containsEntry(Integer.class.getName(), 5L);
    }

    @Test
    void testReset() {
        var stats = new ProcessorStatistics();
        stats.recordIn(String.class, 10, 100);

        stats.reset();

        assertThat(stats.getInCount(String.class)).isZero();
    }
}
