package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.protocol.ServerInfo;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * JetStream stream configuration.
 *
 * @param name            the stream name
 * @param subjects        the subjects this stream captures
 * @param retention       the retention policy (limits, interest, workqueue)
 * @param maxConsumers    maximum number of consumers (-1 for unlimited)
 * @param maxMsgs         maximum number of messages (-1 for unlimited)
 * @param maxBytes        maximum total bytes (-1 for unlimited)
 * @param maxAge          maximum message age (Duration.ZERO for unlimited)
 * @param storage         storage type (memory or file)
 * @param numReplicas     number of replicas
 * @param discardPolicy   discard policy when limits reached (old or new)
 * @param duplicateWindow window for duplicate message detection
 * @since 1.0.0
 */
public record StreamConfig(
        String name,
        List<String> subjects,
        RetentionPolicy retention,
        int maxConsumers,
        long maxMsgs,
        long maxBytes,
        Duration maxAge,
        StorageType storage,
        int numReplicas,
        DiscardPolicy discardPolicy,
        Duration duplicateWindow
) {

    /**
     * Retention policies for stream messages.
     */
    public enum RetentionPolicy {
        /** Messages retained based on limits (max_msgs, max_bytes, max_age). */
        LIMITS("limits"),
        /** Messages retained only while there are active consumers. */
        INTEREST("interest"),
        /** Messages removed after being consumed (work queue pattern). */
        WORKQUEUE("workqueue");

        private final String value;
        RetentionPolicy(String value) { this.value = value; }
        public String value() { return value; }

        public static RetentionPolicy fromValue(String v) {
            for (RetentionPolicy p : values()) {
                if (p.value.equals(v)) return p;
            }
            return LIMITS;
        }
    }

    /**
     * Storage types for stream data.
     */
    public enum StorageType {
        /** In-memory storage. */
        MEMORY("memory"),
        /** File-based storage (not implemented in this version). */
        FILE("file");

        private final String value;
        StorageType(String value) { this.value = value; }
        public String value() { return value; }

        public static StorageType fromValue(String v) {
            if ("file".equals(v)) return FILE;
            return MEMORY;
        }
    }

    /**
     * Discard policies when limits are reached.
     */
    public enum DiscardPolicy {
        /** Discard old messages. */
        OLD("old"),
        /** Discard new messages. */
        NEW("new");

        private final String value;
        DiscardPolicy(String value) { this.value = value; }
        public String value() { return value; }

        public static DiscardPolicy fromValue(String v) {
            if ("new".equals(v)) return NEW;
            return OLD;
        }
    }

    /**
     * Creates a stream config with validation.
     */
    public StreamConfig {
        Objects.requireNonNull(name, "stream name must not be null");
        if (name.isEmpty()) throw new IllegalArgumentException("stream name must not be empty");
        Objects.requireNonNull(subjects, "subjects must not be null");
        if (subjects.isEmpty()) throw new IllegalArgumentException("at least one subject required");
        if (retention == null) retention = RetentionPolicy.LIMITS;
        if (storage == null) storage = StorageType.MEMORY;
        if (discardPolicy == null) discardPolicy = DiscardPolicy.OLD;
        if (maxAge == null) maxAge = Duration.ZERO;
        if (duplicateWindow == null) duplicateWindow = Duration.ofMinutes(2);
        subjects = List.copyOf(subjects);
    }

    /**
     * Creates a builder for stream configuration.
     *
     * @param name the stream name
     * @return the builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Encodes this config as JSON.
     *
     * @return JSON string
     */
    public String toJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"name\":\"").append(name).append('"');
        sb.append(",\"subjects\":[");
        for (int i = 0; i < subjects.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(subjects.get(i)).append('"');
        }
        sb.append(']');
        sb.append(",\"retention\":\"").append(retention.value()).append('"');
        sb.append(",\"max_consumers\":").append(maxConsumers);
        sb.append(",\"max_msgs\":").append(maxMsgs);
        sb.append(",\"max_bytes\":").append(maxBytes);
        sb.append(",\"max_age\":").append(maxAge.toNanos());
        sb.append(",\"storage\":\"").append(storage.value()).append('"');
        sb.append(",\"num_replicas\":").append(numReplicas);
        sb.append(",\"discard\":\"").append(discardPolicy.value()).append('"');
        sb.append(",\"duplicate_window\":").append(duplicateWindow.toNanos());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Parses a JSON string into a StreamConfig.
     *
     * @param json the JSON string
     * @return parsed config
     */
    public static StreamConfig fromJson(String json) {
        String name = ServerInfo.extractString(json, "name", "");
        String retention = ServerInfo.extractString(json, "retention", "limits");
        int maxConsumers = ServerInfo.extractInt(json, "max_consumers", -1);
        long maxMsgs = ServerInfo.extractLong(json, "max_msgs", -1);
        long maxBytes = ServerInfo.extractLong(json, "max_bytes", -1);
        long maxAgeNanos = ServerInfo.extractLong(json, "max_age", 0);
        String storage = ServerInfo.extractString(json, "storage", "memory");
        int numReplicas = ServerInfo.extractInt(json, "num_replicas", 1);
        String discard = ServerInfo.extractString(json, "discard", "old");
        long dupWindowNanos = ServerInfo.extractLong(json, "duplicate_window", Duration.ofMinutes(2).toNanos());

        // Parse subjects array
        List<String> subjects = parseSubjectsArray(json);

        return new StreamConfig(name, subjects,
                RetentionPolicy.fromValue(retention),
                maxConsumers, maxMsgs, maxBytes,
                Duration.ofNanos(maxAgeNanos),
                StorageType.fromValue(storage),
                numReplicas,
                DiscardPolicy.fromValue(discard),
                Duration.ofNanos(dupWindowNanos));
    }

    private static List<String> parseSubjectsArray(String json) {
        int start = json.indexOf("\"subjects\":[");
        if (start < 0) return List.of(">");
        start = json.indexOf('[', start) + 1;
        int end = json.indexOf(']', start);
        if (end <= start) return List.of(">");
        String arrContent = json.substring(start, end);
        var result = new java.util.ArrayList<String>();
        for (String part : arrContent.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
                result.add(trimmed.substring(1, trimmed.length() - 1));
            }
        }
        return result.isEmpty() ? List.of(">") : result;
    }

    /**
     * Builder for StreamConfig.
     */
    public static final class Builder {
        private final String name;
        private List<String> subjects = List.of(">");
        private RetentionPolicy retention = RetentionPolicy.LIMITS;
        private int maxConsumers = -1;
        private long maxMsgs = -1;
        private long maxBytes = -1;
        private Duration maxAge = Duration.ZERO;
        private StorageType storage = StorageType.MEMORY;
        private int numReplicas = 1;
        private DiscardPolicy discardPolicy = DiscardPolicy.OLD;
        private Duration duplicateWindow = Duration.ofMinutes(2);

        Builder(String name) { this.name = name; }

        public Builder subjects(String... subjects) { this.subjects = List.of(subjects); return this; }
        public Builder subjects(List<String> subjects) { this.subjects = subjects; return this; }
        public Builder retention(RetentionPolicy r) { this.retention = r; return this; }
        public Builder maxConsumers(int n) { this.maxConsumers = n; return this; }
        public Builder maxMsgs(long n) { this.maxMsgs = n; return this; }
        public Builder maxBytes(long n) { this.maxBytes = n; return this; }
        public Builder maxAge(Duration d) { this.maxAge = d; return this; }
        public Builder storage(StorageType s) { this.storage = s; return this; }
        public Builder numReplicas(int n) { this.numReplicas = n; return this; }
        public Builder discardPolicy(DiscardPolicy d) { this.discardPolicy = d; return this; }
        public Builder duplicateWindow(Duration d) { this.duplicateWindow = d; return this; }

        public StreamConfig build() {
            return new StreamConfig(name, subjects, retention, maxConsumers,
                    maxMsgs, maxBytes, maxAge, storage, numReplicas,
                    discardPolicy, duplicateWindow);
        }
    }
}
