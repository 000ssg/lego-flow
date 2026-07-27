package ssg.legoflow.http2.hpack;

import java.util.ArrayDeque;
import java.util.Deque;

public class HpackDynamicTable {

    private static final int ENTRY_OVERHEAD = 32;

    private final Deque<HpackStaticTable.Entry> entries = new ArrayDeque<>();
    private int maxSize;
    private int currentSize;

    public HpackDynamicTable(int maxSize) {
        this.maxSize = maxSize;
    }

    public void add(String name, String value) {
        int entrySize = name.length() + value.length() + ENTRY_OVERHEAD;
        while (currentSize + entrySize > maxSize && !entries.isEmpty()) {
            evict();
        }
        if (entrySize <= maxSize) {
            entries.addFirst(new HpackStaticTable.Entry(name, value));
            currentSize += entrySize;
        }
    }

    public HpackStaticTable.Entry get(int index) {
        int dynamicIndex = index - HpackStaticTable.SIZE;
        if (dynamicIndex < 1 || dynamicIndex > entries.size()) {
            throw new IllegalArgumentException("Invalid dynamic table index: " + index);
        }
        int i = 1;
        for (var entry : entries) {
            if (i == dynamicIndex) return entry;
            i++;
        }
        throw new IllegalStateException("Unreachable");
    }

    public int size() {
        return entries.size();
    }

    public int currentSize() {
        return currentSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public void setMaxSize(int newMaxSize) {
        this.maxSize = newMaxSize;
        while (currentSize > maxSize && !entries.isEmpty()) {
            evict();
        }
    }

    public int findNameValueIndex(String name, String value) {
        int i = HpackStaticTable.SIZE + 1;
        for (var entry : entries) {
            if (entry.name().equals(name) && entry.value().equals(value)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    public int findNameIndex(String name) {
        int i = HpackStaticTable.SIZE + 1;
        for (var entry : entries) {
            if (entry.name().equals(name)) {
                return i;
            }
            i++;
        }
        return 0;
    }

    private void evict() {
        var last = entries.removeLast();
        currentSize -= (last.name().length() + last.value().length() + ENTRY_OVERHEAD);
    }

    public void clear() {
        entries.clear();
        currentSize = 0;
    }
}
