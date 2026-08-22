package ssg.legoflow.messaging.nats.jetstream;

import ssg.legoflow.messaging.nats.client.NatsMessage;
import ssg.legoflow.messaging.nats.protocol.NatsHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
/**
 * Pull-based JetStream consumer subscription.
 *
 * <p>Allows fetching batches of messages from a stream consumer.
 * Messages are delivered with metadata headers including stream
 * sequence numbers and can be individually acknowledged.
 *
 * @since 0.1.0
 */
public final class PullSubscription {

    private final Stream stream;
    private final Consumer consumer;

    /**
     * Creates a new pull subscription.
     *
     * @param stream   the stream
     * @param consumer the consumer
     */
    public PullSubscription(Stream stream, Consumer consumer) {
        this.stream = Objects.requireNonNull(stream);
        this.consumer = Objects.requireNonNull(consumer);
    }

    /**
     * Returns the stream.
     *
     * @return the stream
     */
    public Stream stream() {
        return stream;
    }

    /**
     * Returns the consumer.
     *
     * @return the consumer
     */
    public Consumer consumer() {
        return consumer;
    }

    /**
     * Fetches up to N messages from the stream.
     *
     * @param maxMessages the maximum number of messages to fetch
     * @return list of messages, possibly empty
     */
    public List<NatsMessage> fetch(int maxMessages) {
        if (!consumer.canDeliver()) {
            return List.of();
        }

        long fromSeq = consumer.nextFetchSequence();
        String filterSubject = consumer.config().filterSubject();

        List<StreamStore.StoredMessage> stored;
        if (filterSubject != null) {
            stored = stream.store().fetch(filterSubject, fromSeq, maxMessages);
        } else {
            stored = stream.store().fetch(fromSeq, maxMessages);
        }

        if (stored.isEmpty()) {
            return List.of();
        }

        var result = new ArrayList<NatsMessage>(stored.size());
        for (var msg : stored) {
            consumer.markDelivered(msg.sequence());

            // Add JetStream metadata headers
            var headers = new NatsHeaders();
            headers.set("Nats-Stream", stream.name());
            headers.set("Nats-Sequence", String.valueOf(msg.sequence()));
            headers.set("Nats-Timestamp", msg.timestamp().toString());

            // Merge original headers if present
            if (msg.headers() != null) {
                for (String key : msg.headers().keys()) {
                    for (String value : msg.headers().getAll(key)) {
                        headers.add(key, value);
                    }
                }
            }

            result.add(new NatsMessage(msg.subject(), null, headers, msg.payload()));
        }

        return result;
    }

    /**
     * Acknowledges a message by its stream sequence number.
     *
     * @param sequence the sequence to acknowledge
     */
    public void ack(long sequence) {
        consumer.acknowledge(sequence);

        // For workqueue retention, remove message after ack
        if (stream.config().retention() == StreamConfig.RetentionPolicy.WORKQUEUE) {
            stream.store().remove(sequence);
        }
    }

    /**
     * Acknowledges a message extracted from its headers.
     *
     * @param message the message to acknowledge
     */
    public void ack(NatsMessage message) {
        if (message.headers() != null) {
            String seqStr = message.headers().getFirst("Nats-Sequence");
            if (seqStr != null) {
                ack(Long.parseLong(seqStr));
            }
        }
    }
}
