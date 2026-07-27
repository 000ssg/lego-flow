package ssg.legoflow.messaging.nats.jetstream;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JetStream stream definition managing configuration, storage, and consumers.
 *
 * @since 1.0.0
 */
public final class Stream {

    private final StreamConfig config;
    private final StreamStore store;
    private final Map<String, Consumer> consumers = new ConcurrentHashMap<>();

    /**
     * Creates a new stream with the given configuration.
     *
     * @param config the stream configuration
     */
    public Stream(StreamConfig config) {
        this.config = Objects.requireNonNull(config);
        this.store = new StreamStore(config);
    }

    /**
     * Returns the stream configuration.
     *
     * @return the config
     */
    public StreamConfig config() {
        return config;
    }

    /**
     * Returns the stream name.
     *
     * @return the name
     */
    public String name() {
        return config.name();
    }

    /**
     * Returns the message store.
     *
     * @return the store
     */
    public StreamStore store() {
        return store;
    }

    /**
     * Adds a consumer to this stream.
     *
     * @param consumer the consumer
     */
    public void addConsumer(Consumer consumer) {
        if (config.maxConsumers() > 0 && consumers.size() >= config.maxConsumers()) {
            throw new IllegalStateException("Maximum consumers reached: " + config.maxConsumers());
        }
        consumers.put(consumer.name(), consumer);
    }

    /**
     * Removes a consumer from this stream.
     *
     * @param name the consumer name
     * @return the removed consumer, or null
     */
    public Consumer removeConsumer(String name) {
        return consumers.remove(name);
    }

    /**
     * Returns a consumer by name.
     *
     * @param name the consumer name
     * @return the consumer, or null
     */
    public Consumer getConsumer(String name) {
        return consumers.get(name);
    }

    /**
     * Returns all consumer names.
     *
     * @return list of consumer names
     */
    public java.util.List<String> consumerNames() {
        return java.util.List.copyOf(consumers.keySet());
    }

    /**
     * Returns the number of consumers.
     *
     * @return the count
     */
    public int consumerCount() {
        return consumers.size();
    }

    /**
     * Checks whether a subject matches any of this stream's subject filters.
     *
     * @param subject the subject to check
     * @return true if matched
     */
    public boolean matchesSubject(String subject) {
        for (String streamSubject : config.subjects()) {
            if (ssg.legoflow.messaging.nats.subject.SubjectMatcher.matches(streamSubject, subject)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns stream info as JSON.
     *
     * @return JSON string
     */
    public String toInfoJson() {
        var sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"config\":").append(config.toJson());
        sb.append(",\"state\":{");
        sb.append("\"messages\":").append(store.messageCount());
        sb.append(",\"bytes\":").append(store.totalBytes());
        sb.append(",\"first_seq\":").append(store.firstMessage() != null ? store.firstMessage().sequence() : 0);
        sb.append(",\"last_seq\":").append(store.currentSequence());
        sb.append(",\"consumer_count\":").append(consumers.size());
        sb.append('}');
        sb.append('}');
        return sb.toString();
    }
}
