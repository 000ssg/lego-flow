package ssg.legoflow.database.redis.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Redis Stream implementation with consumer groups.
 *
 * <p>Entries are stored in a sorted map keyed by stream entry IDs
 * (millisecond timestamp + sequence number). Consumer groups track
 * delivered and acknowledged entries.
 *
 * @since 0.1.0
 */
public final class StreamStore {

    private final NavigableMap<String, Map<String, String>> entries = new ConcurrentSkipListMap<>(StreamStore::compareIds);
    private final Map<String, ConsumerGroup> groups = new ConcurrentHashMap<>();
    private final AtomicLong lastTimestamp = new AtomicLong(0);
    private final AtomicLong lastSequence = new AtomicLong(0);

    /**
     * A stream entry with its ID and field-value pairs.
     *
     * @param id     the entry ID (ms-seq)
     * @param fields the field-value pairs
     */
    public record StreamEntry(String id, Map<String, String> fields) {}

    /**
     * A consumer group tracking pending entries and consumers.
     */
    public static final class ConsumerGroup {
        private final String name;
        private volatile String lastDeliveredId;
        private final Map<String, PendingEntry> pending = new ConcurrentHashMap<>();
        private final Set<String> consumers = ConcurrentHashMap.newKeySet();

        ConsumerGroup(String name, String lastDeliveredId) {
            this.name = name;
            this.lastDeliveredId = lastDeliveredId;
        }

        public String name() { return name; }
        public String lastDeliveredId() { return lastDeliveredId; }
        public void setLastDeliveredId(String id) { this.lastDeliveredId = id; }
        public Map<String, PendingEntry> pending() { return pending; }
        public Set<String> consumers() { return consumers; }
    }

    /**
     * A pending entry awaiting acknowledgement.
     *
     * @param id           the entry ID
     * @param consumer     the consumer name
     * @param deliveryTime delivery timestamp
     * @param deliveryCount number of deliveries
     */
    public record PendingEntry(String id, String consumer, long deliveryTime, int deliveryCount) {}

    /**
     * Adds an entry to the stream.
     *
     * @param id     the entry ID, or "*" for auto-generated
     * @param fields the field-value pairs
     * @return the assigned entry ID
     */
    public String add(String id, Map<String, String> fields) {
        String resolvedId;
        if ("*".equals(id)) {
            resolvedId = generateId();
        } else {
            resolvedId = resolvePartialId(id);
        }
        entries.put(resolvedId, new LinkedHashMap<>(fields));
        return resolvedId;
    }

    /**
     * Returns the number of entries in the stream.
     *
     * @return entry count
     */
    public long length() {
        return entries.size();
    }

    /**
     * Returns entries in the given ID range (inclusive).
     *
     * @param start start ID ("-" for minimum)
     * @param end   end ID ("+" for maximum)
     * @param count maximum entries to return (0 for unlimited)
     * @return matching entries
     */
    public List<StreamEntry> range(String start, String end, int count) {
        String from = "-".equals(start) ? "" : start;
        String to = "+".equals(end) ? "99999999999999-99999999999999" : end;

        NavigableMap<String, Map<String, String>> sub = entries.subMap(from, true, to, true);
        List<StreamEntry> result = new ArrayList<>();
        for (var entry : sub.entrySet()) {
            result.add(new StreamEntry(entry.getKey(), entry.getValue()));
            if (count > 0 && result.size() >= count) break;
        }
        return result;
    }

    /**
     * Returns entries in reverse order.
     *
     * @param end   end ID ("+" for maximum)
     * @param start start ID ("-" for minimum)
     * @param count maximum entries
     * @return matching entries in reverse order
     */
    public List<StreamEntry> revRange(String end, String start, int count) {
        String from = "-".equals(start) ? "" : start;
        String to = "+".equals(end) ? "99999999999999-99999999999999" : end;

        NavigableMap<String, Map<String, String>> sub = entries.subMap(from, true, to, true).descendingMap();
        List<StreamEntry> result = new ArrayList<>();
        for (var entry : sub.entrySet()) {
            result.add(new StreamEntry(entry.getKey(), entry.getValue()));
            if (count > 0 && result.size() >= count) break;
        }
        return result;
    }

    /**
     * Reads entries with IDs strictly greater than the given ID.
     *
     * @param lastId the last seen ID ("0" or "0-0" for beginning)
     * @param count  maximum entries to return
     * @return new entries
     */
    public List<StreamEntry> read(String lastId, int count) {
        String from = "0".equals(lastId) || "0-0".equals(lastId) ? "" : lastId;
        NavigableMap<String, Map<String, String>> tail = entries.tailMap(from, false);
        List<StreamEntry> result = new ArrayList<>();
        for (var entry : tail.entrySet()) {
            result.add(new StreamEntry(entry.getKey(), entry.getValue()));
            if (count > 0 && result.size() >= count) break;
        }
        return result;
    }

    /**
     * Trims the stream to at most maxLen entries (keeping newest).
     *
     * @param maxLen maximum number of entries to keep
     * @return number of entries removed
     */
    public long trim(long maxLen) {
        long toRemove = entries.size() - maxLen;
        if (toRemove <= 0) return 0;
        long removed = 0;
        Iterator<String> it = entries.keySet().iterator();
        while (it.hasNext() && removed < toRemove) {
            it.next();
            it.remove();
            removed++;
        }
        return removed;
    }

    // ---- Consumer groups ----

    /**
     * Creates a consumer group.
     *
     * @param name          the group name
     * @param lastDeliveredId the ID from which to start reading ("$" for latest, "0" for beginning)
     * @return true if created, false if already exists
     */
    public boolean createGroup(String name, String lastDeliveredId) {
        String startId = "$".equals(lastDeliveredId) ? lastEntryId() : lastDeliveredId;
        if (startId == null) startId = "0-0";
        return groups.putIfAbsent(name, new ConsumerGroup(name, startId)) == null;
    }

    /**
     * Destroys a consumer group.
     *
     * @param name the group name
     * @return true if destroyed
     */
    public boolean destroyGroup(String name) {
        return groups.remove(name) != null;
    }

    /**
     * Removes a consumer from a group.
     *
     * @param groupName    the group name
     * @param consumerName the consumer name
     * @return number of pending entries that were released
     */
    public long deleteConsumer(String groupName, String consumerName) {
        ConsumerGroup group = groups.get(groupName);
        if (group == null) return 0;
        group.consumers().remove(consumerName);
        long count = 0;
        Iterator<Map.Entry<String, PendingEntry>> it = group.pending().entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().consumer().equals(consumerName)) {
                it.remove();
                count++;
            }
        }
        return count;
    }

    /**
     * Gets a consumer group by name.
     *
     * @param name the group name
     * @return the group, or null
     */
    public ConsumerGroup getGroup(String name) {
        return groups.get(name);
    }

    /**
     * Returns all consumer groups.
     *
     * @return unmodifiable collection of groups
     */
    public Collection<ConsumerGroup> groups() {
        return Collections.unmodifiableCollection(groups.values());
    }

    /**
     * Reads entries for a consumer group, delivering new messages to the consumer.
     *
     * @param groupName    the group name
     * @param consumerName the consumer name
     * @param count        max entries to return
     * @param lastId       ">" for new messages, or an ID for pending
     * @return entries for this consumer
     */
    public List<StreamEntry> readGroup(String groupName, String consumerName, int count, String lastId) {
        ConsumerGroup group = groups.get(groupName);
        if (group == null) return List.of();

        group.consumers().add(consumerName);

        if (">".equals(lastId)) {
            // Deliver new (undelivered) messages
            String from = group.lastDeliveredId();
            NavigableMap<String, Map<String, String>> tail = entries.tailMap(from, false);
            List<StreamEntry> result = new ArrayList<>();
            for (var entry : tail.entrySet()) {
                result.add(new StreamEntry(entry.getKey(), entry.getValue()));
                group.pending().put(entry.getKey(),
                        new PendingEntry(entry.getKey(), consumerName, System.currentTimeMillis(), 1));
                group.setLastDeliveredId(entry.getKey());
                if (count > 0 && result.size() >= count) break;
            }
            return result;
        } else {
            // Return pending messages for this consumer
            List<StreamEntry> result = new ArrayList<>();
            for (var pe : group.pending().values()) {
                if (pe.consumer().equals(consumerName) && compareIds(pe.id(), lastId) > 0) {
                    Map<String, String> fields = entries.get(pe.id());
                    if (fields != null) {
                        result.add(new StreamEntry(pe.id(), fields));
                    }
                    if (count > 0 && result.size() >= count) break;
                }
            }
            return result;
        }
    }

    /**
     * Acknowledges entries in a consumer group.
     *
     * @param groupName the group name
     * @param ids       entry IDs to acknowledge
     * @return number of entries acknowledged
     */
    public long acknowledge(String groupName, String... ids) {
        ConsumerGroup group = groups.get(groupName);
        if (group == null) return 0;
        long count = 0;
        for (String id : ids) {
            if (group.pending().remove(id) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns pending entries for a consumer group.
     *
     * @param groupName the group name
     * @return pending entries
     */
    public Collection<PendingEntry> pending(String groupName) {
        ConsumerGroup group = groups.get(groupName);
        if (group == null) return List.of();
        return Collections.unmodifiableCollection(group.pending().values());
    }

    /**
     * Claims pending entries for a different consumer.
     *
     * @param groupName    the group name
     * @param consumerName the new consumer
     * @param minIdleTime  minimum idle time in ms
     * @param ids          entry IDs to claim
     * @return claimed entries
     */
    public List<StreamEntry> claim(String groupName, String consumerName, long minIdleTime, String... ids) {
        ConsumerGroup group = groups.get(groupName);
        if (group == null) return List.of();
        group.consumers().add(consumerName);
        List<StreamEntry> claimed = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : ids) {
            PendingEntry pe = group.pending().get(id);
            if (pe != null && (now - pe.deliveryTime()) >= minIdleTime) {
                group.pending().put(id, new PendingEntry(id, consumerName, now, pe.deliveryCount() + 1));
                Map<String, String> fields = entries.get(id);
                if (fields != null) {
                    claimed.add(new StreamEntry(id, fields));
                }
            }
        }
        return claimed;
    }

    // ---- ID helpers ----

    private String generateId() {
        long now = System.currentTimeMillis();
        long seq;
        synchronized (this) {
            if (now > lastTimestamp.get()) {
                lastTimestamp.set(now);
                lastSequence.set(0);
                seq = 0;
            } else {
                seq = lastSequence.incrementAndGet();
            }
        }
        return now + "-" + seq;
    }

    private String resolvePartialId(String id) {
        if (id.contains("-")) {
            return id;
        }
        return id + "-0";
    }

    private String lastEntryId() {
        if (entries.isEmpty()) return null;
        return entries.lastKey();
    }

    static int compareIds(String a, String b) {
        String[] pa = a.split("-", 2);
        String[] pb = b.split("-", 2);
        long ta = Long.parseLong(pa[0]);
        long tb = Long.parseLong(pb[0]);
        int cmp = Long.compare(ta, tb);
        if (cmp != 0) return cmp;
        long sa = pa.length > 1 ? Long.parseLong(pa[1]) : 0;
        long sb = pb.length > 1 ? Long.parseLong(pb[1]) : 0;
        return Long.compare(sa, sb);
    }
}
