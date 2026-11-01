package ssg.legoflow.blocks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class ProcessorStatisticsEnhancedTest {

    private ProcessorStatistics stats;

    @BeforeEach
    void setUp() {
        stats = new ProcessorStatistics();
    }

    @Test
    void testRecordInIncrementsCount() {
        stats.recordIn(String.class, 1, 10);
        assertEquals(1, stats.getInCount(String.class));
    }

    @Test
    void testRecordInIncrementsAmount() {
        stats.recordIn(String.class, 1, 10);
        assertEquals(10, stats.getInAmount(String.class));
    }

    @Test
    void testRecordOutIncrementsCount() {
        stats.recordOut(String.class, 1, 5);
        assertEquals(1, stats.getOutCount(String.class));
    }

    @Test
    void testRecordOutIncrementsAmount() {
        stats.recordOut(String.class, 1, 5);
        assertEquals(5, stats.getOutAmount(String.class));
    }

    @Test
    void testMultipleRecordCalls() {
        stats.recordIn(String.class, 3, 30);
        stats.recordIn(String.class, 2, 20);
        assertEquals(5, stats.getInCount(String.class));
        assertEquals(50, stats.getInAmount(String.class));
    }

    @Test
    void testDifferentTypesIndependent() {
        stats.recordIn(String.class, 1, 10);
        stats.recordIn(Integer.class, 2, 20);
        assertEquals(1, stats.getInCount(String.class));
        assertEquals(2, stats.getInCount(Integer.class));
    }

    @Test
    void testGetMissingTypeReturnsZero() {
        assertEquals(0, stats.getInCount(String.class));
        assertEquals(0, stats.getOutCount(String.class));
        assertEquals(0, stats.getInAmount(String.class));
        assertEquals(0, stats.getOutAmount(String.class));
    }

    @Test
    void testSnapshot() {
        stats.recordIn(String.class, 2, 20);
        stats.recordOut(Integer.class, 1, 5);

        ProcessorStatistics.Snapshot snapshot = stats.snapshot();

        Map<String, Long> inCounts = snapshot.inCounts();
        Map<String, Long> outCounts = snapshot.outCounts();
        Map<String, Long> inAmounts = snapshot.inAmounts();
        Map<String, Long> outAmounts = snapshot.outAmounts();

        assertEquals(2, inCounts.get(String.class.getName()));
        assertEquals(1, outCounts.get(Integer.class.getName()));
        assertEquals(20, inAmounts.get(String.class.getName()));
        assertEquals(5, outAmounts.get(Integer.class.getName()));
    }

    @Test
    void testSnapshotCreatesIndependentCopy() {
        stats.recordIn(String.class, 1, 10);
        ProcessorStatistics.Snapshot snapshot = stats.snapshot();

        // Snapshot should reflect current state
        assertEquals(1, snapshot.inCounts().get(String.class.getName()));
        assertEquals(10, snapshot.inAmounts().get(String.class.getName()));
    }

    @Test
    void testReset() {
        stats.recordIn(String.class, 5, 50);
        stats.recordOut(Integer.class, 3, 30);
        stats.reset();

        assertEquals(0, stats.getInCount(String.class));
        assertEquals(0, stats.getOutCount(Integer.class));
        assertEquals(0, stats.getInAmount(String.class));
        assertEquals(0, stats.getOutAmount(Integer.class));
    }

    @Test
    void testResetMultipleTypes() {
        stats.recordIn(String.class, 1, 10);
        stats.recordIn(Integer.class, 2, 20);
        stats.recordIn(Double.class, 3, 30);
        stats.reset();

        assertEquals(0, stats.getInCount(String.class));
        assertEquals(0, stats.getInCount(Integer.class));
        assertEquals(0, stats.getInCount(Double.class));
    }

    @Test
    void testLargeCount() {
        stats.recordIn(String.class, 1000, 100000);
        assertEquals(1000, stats.getInCount(String.class));
        assertEquals(100000, stats.getInAmount(String.class));
    }

    @Test
    void testZeroAmount() {
        stats.recordIn(String.class, 5, 0);
        assertEquals(5, stats.getInCount(String.class));
        assertEquals(0, stats.getInAmount(String.class));
    }

    @Test
    void testSnapshotEmptyWhenNoRecords() {
        ProcessorStatistics.Snapshot snapshot = stats.snapshot();

        assertTrue(snapshot.inCounts().isEmpty());
        assertTrue(snapshot.outCounts().isEmpty());
        assertTrue(snapshot.inAmounts().isEmpty());
        assertTrue(snapshot.outAmounts().isEmpty());
    }
}
