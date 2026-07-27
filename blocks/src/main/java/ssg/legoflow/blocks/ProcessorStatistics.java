package ssg.legoflow.blocks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ProcessorStatistics {

    private final ConcurrentHashMap<String, AtomicLong> inCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> outCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> inAmounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> outAmounts = new ConcurrentHashMap<>();

    public void recordIn(Class<?> type, long count, long amount) {
        var key = type.getName();
        inCounts.computeIfAbsent(key, _ -> new AtomicLong()).addAndGet(count);
        inAmounts.computeIfAbsent(key, _ -> new AtomicLong()).addAndGet(amount);
    }

    public void recordOut(Class<?> type, long count, long amount) {
        var key = type.getName();
        outCounts.computeIfAbsent(key, _ -> new AtomicLong()).addAndGet(count);
        outAmounts.computeIfAbsent(key, _ -> new AtomicLong()).addAndGet(amount);
    }

    public long getInCount(Class<?> type) {
        var counter = inCounts.get(type.getName());
        return counter != null ? counter.get() : 0;
    }

    public long getOutCount(Class<?> type) {
        var counter = outCounts.get(type.getName());
        return counter != null ? counter.get() : 0;
    }

    public long getInAmount(Class<?> type) {
        var counter = inAmounts.get(type.getName());
        return counter != null ? counter.get() : 0;
    }

    public long getOutAmount(Class<?> type) {
        var counter = outAmounts.get(type.getName());
        return counter != null ? counter.get() : 0;
    }

    public record Snapshot(Map<String, Long> inCounts, Map<String, Long> outCounts,
                           Map<String, Long> inAmounts, Map<String, Long> outAmounts) {}

    public Snapshot snapshot() {
        return new Snapshot(
                snapshotMap(inCounts),
                snapshotMap(outCounts),
                snapshotMap(inAmounts),
                snapshotMap(outAmounts)
        );
    }

    public void reset() {
        inCounts.clear();
        outCounts.clear();
        inAmounts.clear();
        outAmounts.clear();
    }

    private static Map<String, Long> snapshotMap(ConcurrentHashMap<String, AtomicLong> source) {
        var result = new java.util.HashMap<String, Long>(source.size());
        source.forEach((k, v) -> result.put(k, v.get()));
        return Map.copyOf(result);
    }
}
