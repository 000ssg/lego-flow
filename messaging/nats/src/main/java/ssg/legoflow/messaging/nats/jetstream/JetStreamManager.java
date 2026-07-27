package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import ssg.legoflow.messaging.nats.protocol.NatsProtocol;
import ssg.legoflow.messaging.nats.server.ClientConnection;
import ssg.legoflow.messaging.nats.server.NatsServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JetStream stream and consumer management.
 *
 * <p>Handles stream CRUD operations, consumer management, and message
 * persistence. Integrates with the NATS server to intercept published
 * messages matching stream subjects and handle JetStream API requests
 * on {@code $JS.API.*} subjects.
 *
 * @since 1.0.0
 */
public final class JetStreamManager {

    private static final Logger LOG = LoggerFactory.getLogger(JetStreamManager.class);

    private final NatsServer server;
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();

    /**
     * Creates a new JetStream manager.
     *
     * @param server the owning NATS server
     */
    public JetStreamManager(NatsServer server) {
        this.server = server;
    }

    // --- Stream operations ---

    /**
     * Creates a new stream.
     *
     * @param config the stream configuration
     * @return the created stream
     * @throws IllegalArgumentException if stream already exists
     */
    public Stream createStream(StreamConfig config) {
        if (streams.containsKey(config.name())) {
            throw new IllegalArgumentException("Stream already exists: " + config.name());
        }
        var stream = new Stream(config);
        streams.put(config.name(), stream);
        LOG.info("Created stream '{}' with subjects {}", config.name(), config.subjects());
        return stream;
    }

    /**
     * Updates an existing stream configuration.
     *
     * @param config the new configuration
     * @return the updated stream
     * @throws IllegalArgumentException if stream does not exist
     */
    public Stream updateStream(StreamConfig config) {
        var existing = streams.get(config.name());
        if (existing == null) {
            throw new IllegalArgumentException("Stream not found: " + config.name());
        }
        // For simplicity, replace the stream (keeping existing messages is complex)
        var stream = new Stream(config);
        streams.put(config.name(), stream);
        LOG.info("Updated stream '{}'", config.name());
        return stream;
    }

    /**
     * Deletes a stream.
     *
     * @param name the stream name
     * @return true if deleted
     */
    public boolean deleteStream(String name) {
        var removed = streams.remove(name);
        if (removed != null) {
            LOG.info("Deleted stream '{}'", name);
            return true;
        }
        return false;
    }

    /**
     * Returns stream info.
     *
     * @param name the stream name
     * @return the stream, or null
     */
    public Stream getStream(String name) {
        return streams.get(name);
    }

    /**
     * Returns all stream names.
     *
     * @return list of stream names
     */
    public List<String> streamNames() {
        return List.copyOf(streams.keySet());
    }

    /**
     * Returns all streams.
     *
     * @return list of streams
     */
    public List<Stream> streams() {
        return List.copyOf(streams.values());
    }

    /**
     * Purges all messages from a stream.
     *
     * @param name the stream name
     * @return the number of purged messages, or -1 if not found
     */
    public int purgeStream(String name) {
        var stream = streams.get(name);
        if (stream == null) return -1;
        int purged = stream.store().purge();
        LOG.info("Purged {} messages from stream '{}'", purged, name);
        return purged;
    }

    // --- Consumer operations ---

    /**
     * Creates a consumer for a stream.
     *
     * @param streamName the stream name
     * @param config     the consumer configuration
     * @return the created consumer
     * @throws IllegalArgumentException if stream not found
     */
    public Consumer createConsumer(String streamName, ConsumerConfig config) {
        var stream = streams.get(streamName);
        if (stream == null) {
            throw new IllegalArgumentException("Stream not found: " + streamName);
        }

        String consumerName = config.durableName() != null
                ? config.durableName()
                : "ephemeral-" + UUID.randomUUID().toString().substring(0, 8);

        // Determine start sequence based on deliver policy
        long startSeq = determineStartSequence(stream, config);

        var consumer = new Consumer(consumerName, config, startSeq);
        stream.addConsumer(consumer);
        LOG.info("Created consumer '{}' on stream '{}'", consumerName, streamName);
        return consumer;
    }

    /**
     * Deletes a consumer.
     *
     * @param streamName   the stream name
     * @param consumerName the consumer name
     * @return true if deleted
     */
    public boolean deleteConsumer(String streamName, String consumerName) {
        var stream = streams.get(streamName);
        if (stream == null) return false;
        var removed = stream.removeConsumer(consumerName);
        return removed != null;
    }

    /**
     * Returns a consumer.
     *
     * @param streamName   the stream name
     * @param consumerName the consumer name
     * @return the consumer, or null
     */
    public Consumer getConsumer(String streamName, String consumerName) {
        var stream = streams.get(streamName);
        if (stream == null) return null;
        return stream.getConsumer(consumerName);
    }

    /**
     * Creates a pull subscription for a consumer.
     *
     * @param streamName   the stream name
     * @param consumerName the consumer name
     * @return the pull subscription
     * @throws IllegalArgumentException if stream or consumer not found
     */
    public PullSubscription pullSubscribe(String streamName, String consumerName) {
        var stream = streams.get(streamName);
        if (stream == null) {
            throw new IllegalArgumentException("Stream not found: " + streamName);
        }
        var consumer = stream.getConsumer(consumerName);
        if (consumer == null) {
            throw new IllegalArgumentException("Consumer not found: " + consumerName);
        }
        return new PullSubscription(stream, consumer);
    }

    /**
     * Handles a published message, storing it in matching streams.
     *
     * @param subject   the published subject
     * @param headers   the message headers
     * @param payload   the message payload
     * @param publisher the publishing client
     */
    public void handlePublish(String subject, NatsHeaders headers,
                               byte[] payload, ClientConnection publisher) {
        // Don't store JetStream API messages
        if (subject.startsWith(NatsProtocol.JS_API_PREFIX)) return;

        for (var stream : streams.values()) {
            if (stream.matchesSubject(subject)) {
                long seq = stream.store().store(subject, headers, payload);
                if (seq > 0) {
                    LOG.debug("Stored message seq={} in stream '{}' for subject '{}'",
                            seq, stream.name(), subject);
                }
            }
        }
    }

    private long determineStartSequence(Stream stream, ConsumerConfig config) {
        return switch (config.deliverPolicy()) {
            case ALL -> 1;
            case LAST -> {
                var last = stream.store().lastMessage();
                yield last != null ? last.sequence() : 1;
            }
            case NEW -> stream.store().currentSequence() + 1;
            case BY_START_SEQ -> config.startSeq();
            case BY_START_TIME -> 1; // Not fully implemented — fallback to all
        };
    }
}
